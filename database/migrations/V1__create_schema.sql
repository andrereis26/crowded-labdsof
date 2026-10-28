-- The tables of the data model (docs/deliverables/diagrams/data-model.puml).
-- Names are the model's in snake_case; User is app_user, because user is a
-- reserved word. Which service writes each table is in R__grants.sql.

-- ENUM types --------------------------------------------------------------------

CREATE TYPE role AS ENUM ('PARTICIPANT', 'ORGANIZATION_MEMBER', 'ADMINISTRATOR');
CREATE TYPE verification_status AS ENUM ('PENDING', 'VERIFIED', 'REJECTED');
CREATE TYPE event_status AS ENUM (
  'PROPOSED', 'SCHEDULED', 'POSTPONED', 'RELOCATED', 'CANCELLED', 'ONGOING', 'ENDED'
);
CREATE TYPE event_visibility AS ENUM ('PUBLIC', 'TICKETED');
CREATE TYPE event_origin AS ENUM ('ORGANIZATION', 'TICKETING_PLATFORM', 'MUNICIPAL_AGENDA');
CREATE TYPE change_type AS ENUM (
  'CONFIRMATION', 'CANCELLATION', 'POSTPONEMENT', 'TIME_CHANGE', 'VENUE_CHANGE', 'DETAILS_UPDATE'
);
CREATE TYPE request_decision AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
CREATE TYPE import_method AS ENUM ('PDF_UPLOAD', 'EMAIL_PASTE', 'MANUAL_ENTRY');
CREATE TYPE ticket_status AS ENUM ('PENDING_CONFIRMATION', 'CONFIRMED');
CREATE TYPE crowd_level AS ENUM ('LOW', 'MODERATE', 'HIGH', 'FULL');
CREATE TYPE queue_point_kind AS ENUM ('ENTRANCE', 'TICKET_OFFICE', 'BAR', 'TOILET', 'OTHER');
CREATE TYPE source_type AS ENUM (
  'OFFICIAL_SOURCE', 'ORGANIZATION', 'PARTICIPANT_ESTIMATE', 'AI_GENERATED', 'EXTERNAL_SERVICE'
);
CREATE TYPE alert_type AS ENUM (
  'ENTRANCE_CLOSURE', 'CAPACITY_REACHED', 'SCHEDULE_DELAY', 'WEATHER_WARNING', 'OTHER'
);
CREATE TYPE alert_status AS ENUM ('DRAFT', 'SENT');
CREATE TYPE severity AS ENUM ('CRITICAL', 'WARNING', 'INFO');
CREATE TYPE survey_type AS ENUM ('CONFIRMATION', 'CHOICE', 'IDEA');
CREATE TYPE confirmation_answer AS ENUM ('YES', 'NO');
CREATE TYPE confidence_level AS ENUM ('LOW', 'MEDIUM', 'HIGH');
CREATE TYPE incident_status AS ENUM ('OPEN', 'ACKNOWLEDGED', 'RESOLVED');
CREATE TYPE grouping_method AS ENUM ('AI_SIMILARITY', 'CATEGORY_TIME_WINDOW', 'MANUAL_SPLIT');
CREATE TYPE notification_type AS ENUM (
  'OFFICIAL_CHANGE', 'OPERATIONAL_ALERT', 'ORGANIZATION_SURVEY', 'SEVERE_WEATHER',
  'INCIDENT_STATUS', 'RECOGNITION', 'CONFIRMATION_REQUEST'
);

-- Core: identity and access -------------------------------------------------------

CREATE TABLE organization (
  id uuid PRIMARY KEY,
  verified_by_user_id uuid,
  name text NOT NULL,
  verification_status verification_status NOT NULL,
  verified_at timestamptz
);

CREATE TABLE app_user (
  id uuid PRIMARY KEY,
  organization_id uuid REFERENCES organization (id),
  email text NOT NULL,
  password_hash text NOT NULL,
  role role NOT NULL,
  created_at timestamptz NOT NULL,
  location_consent_granted boolean,
  -- One account per email address, whatever its case.
  CONSTRAINT app_user_email_key UNIQUE (email),
  CONSTRAINT app_user_email_lower_case CHECK (email = lower(email))
);

-- Organization and app_user reference each other.
ALTER TABLE organization
  ADD CONSTRAINT organization_verified_by_user_id_fkey
  FOREIGN KEY (verified_by_user_id) REFERENCES app_user (id) ON DELETE SET NULL;

CREATE TABLE api_credential (
  key_id uuid PRIMARY KEY,
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  secret_hash text NOT NULL,
  scope text NOT NULL,
  created_at timestamptz NOT NULL,
  revoked_at timestamptz
);

-- Core: event catalog ---------------------------------------------------------------

CREATE TABLE event (
  id uuid PRIMARY KEY,
  organization_id uuid REFERENCES organization (id),
  name text NOT NULL,
  description text NOT NULL,
  tentative_period text,
  start_date_time timestamptz,
  doors_open_at timestamptz,
  end_date_time timestamptz,
  status event_status NOT NULL,
  capacity integer CHECK (capacity >= 0),
  visibility event_visibility NOT NULL,
  origin event_origin NOT NULL,
  ticketing_source_event_id text UNIQUE,
  last_synced_at timestamptz,
  venue_name text,
  address text,
  latitude double precision CHECK (latitude BETWEEN -90 AND 90),
  longitude double precision CHECK (longitude BETWEEN -180 AND 180),
  crowd_level crowd_level,
  head_count integer CHECK (head_count >= 0),
  crowd_provenance_source_type source_type,
  crowd_provenance_source_name text,
  crowd_provenance_last_updated_at timestamptz,
  crowd_provenance_confirmed boolean,
  -- A PROPOSED event has only a tentative period; any other has a start (FR-076).
  CONSTRAINT event_start_known CHECK (status = 'PROPOSED' OR start_date_time IS NOT NULL),
  CONSTRAINT event_ends_after_start CHECK (end_date_time IS NULL OR end_date_time >= start_date_time)
);

CREATE TABLE event_change (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  type change_type NOT NULL,
  severity severity NOT NULL,
  previous_value jsonb NOT NULL,
  new_value jsonb NOT NULL,
  ticket_still_valid boolean,
  refund_info_url text,
  provenance_source_type source_type NOT NULL,
  provenance_source_name text NOT NULL,
  provenance_last_updated_at timestamptz NOT NULL,
  provenance_confirmed boolean NOT NULL,
  corroborating_source_type source_type,
  corroborated_at timestamptz
);
CREATE INDEX event_change_event_id_idx ON event_change (event_id);

CREATE TABLE ownership_request (
  id uuid PRIMARY KEY,
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  decided_by_user_id uuid REFERENCES app_user (id) ON DELETE SET NULL,
  status request_decision NOT NULL,
  requested_at timestamptz NOT NULL,
  decided_at timestamptz
);

-- Core: participation ----------------------------------------------------------------

-- Ticket and Follow are the two kinds of EventConnection and share its id.
CREATE TABLE event_connection (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  connected_at timestamptz NOT NULL,
  last_prompted_at timestamptz
);
CREATE INDEX event_connection_user_id_idx ON event_connection (user_id);
CREATE INDEX event_connection_event_id_idx ON event_connection (event_id);

CREATE TABLE ticket (
  id uuid PRIMARY KEY REFERENCES event_connection (id) ON DELETE CASCADE,
  gate text,
  sector text,
  seat text,
  -- Encrypted by Core before it is stored (SR-018).
  entry_code bytea,
  import_method import_method NOT NULL,
  status ticket_status NOT NULL
);

CREATE TABLE follow (
  id uuid PRIMARY KEY REFERENCES event_connection (id) ON DELETE CASCADE,
  going boolean NOT NULL
);

CREATE TABLE ticket_extraction (
  id uuid PRIMARY KEY,
  ticket_id uuid NOT NULL UNIQUE REFERENCES ticket (id) ON DELETE CASCADE,
  extracted_fields jsonb NOT NULL,
  field_confidence jsonb NOT NULL,
  ai_generated boolean NOT NULL,
  created_at timestamptz NOT NULL
);

CREATE TABLE event_match_candidate (
  id uuid PRIMARY KEY,
  ticket_extraction_id uuid NOT NULL REFERENCES ticket_extraction (id) ON DELETE CASCADE,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  confidence double precision NOT NULL CHECK (confidence BETWEEN 0 AND 1),
  UNIQUE (ticket_extraction_id, event_id)
);

-- Core: on-site information ------------------------------------------------------------

CREATE TABLE queue_time (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  kind queue_point_kind NOT NULL,
  point_name text NOT NULL,
  wait_minutes integer NOT NULL CHECK (wait_minutes >= 0),
  provenance_source_type source_type NOT NULL,
  provenance_source_name text NOT NULL,
  provenance_last_updated_at timestamptz NOT NULL,
  provenance_confirmed boolean NOT NULL,
  UNIQUE (event_id, kind, point_name)
);

CREATE TABLE weather_forecast (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  date date NOT NULL,
  conditions text NOT NULL,
  temperature double precision NOT NULL,
  severe_weather boolean NOT NULL,
  provenance_source_type source_type NOT NULL,
  provenance_source_name text NOT NULL,
  provenance_last_updated_at timestamptz NOT NULL,
  provenance_confirmed boolean NOT NULL,
  UNIQUE (event_id, date)
);

-- Core: reports, confirmations and incidents ---------------------------------------------
-- Before the organizer communication tables: an alert may point to an incident.

CREATE TABLE report_category (
  code text PRIMARY KEY,
  name text NOT NULL,
  personal boolean NOT NULL,
  prompts_enabled_by_default boolean NOT NULL,
  -- Personal categories never trigger confirmation prompts (FR-059).
  CONSTRAINT report_category_personal_without_prompts
    CHECK (NOT (personal AND prompts_enabled_by_default))
);

CREATE TABLE event_category_setting (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  report_category_code text NOT NULL REFERENCES report_category (code),
  prompts_enabled boolean NOT NULL,
  UNIQUE (event_id, report_category_code)
);

CREATE TABLE incident (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  summary text NOT NULL,
  status incident_status NOT NULL,
  grouping_method grouping_method NOT NULL,
  created_at timestamptz NOT NULL
);
CREATE INDEX incident_event_id_idx ON incident (event_id);

CREATE TABLE report (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  report_category_code text NOT NULL REFERENCES report_category (code),
  incident_id uuid REFERENCES incident (id) ON DELETE SET NULL,
  description text,
  reported_crowd_level crowd_level,
  reported_queue_point text,
  reported_wait_minutes integer CHECK (reported_wait_minutes >= 0),
  reported_at timestamptz NOT NULL,
  location_verified boolean NOT NULL,
  confidence_score double precision NOT NULL CHECK (confidence_score BETWEEN 0 AND 1),
  confidence_level confidence_level NOT NULL,
  confidence_computed_at timestamptz NOT NULL
);
CREATE INDEX report_event_id_idx ON report (event_id);
CREATE INDEX report_incident_id_idx ON report (incident_id);

CREATE TABLE confirmation (
  id uuid PRIMARY KEY,
  report_id uuid NOT NULL REFERENCES report (id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  answer confirmation_answer NOT NULL,
  answered_at timestamptz NOT NULL,
  location_verified boolean NOT NULL,
  UNIQUE (report_id, user_id)
);

CREATE TABLE recognition (
  id uuid PRIMARY KEY,
  report_id uuid NOT NULL UNIQUE REFERENCES report (id) ON DELETE CASCADE,
  impact text NOT NULL,
  reached_users integer CHECK (reached_users >= 0),
  granted_at timestamptz NOT NULL
);

-- Core: organizer communication -----------------------------------------------------------

CREATE TABLE alert (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  organization_id uuid NOT NULL REFERENCES organization (id),
  incident_id uuid REFERENCES incident (id) ON DELETE SET NULL,
  type alert_type NOT NULL,
  severity severity NOT NULL,
  message text NOT NULL,
  status alert_status NOT NULL,
  sent_at timestamptz,
  resolved_at timestamptz,
  recipients_count integer CHECK (recipients_count >= 0)
);
CREATE INDEX alert_event_id_idx ON alert (event_id);

CREATE TABLE alert_draft (
  id uuid PRIMARY KEY,
  alert_id uuid NOT NULL UNIQUE REFERENCES alert (id) ON DELETE CASCADE,
  organizer_input text NOT NULL,
  draft_text text NOT NULL,
  facts_used jsonb NOT NULL,
  ai_generated boolean NOT NULL
);

CREATE TABLE event_survey (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  type survey_type NOT NULL,
  text text NOT NULL,
  urgent boolean NOT NULL,
  published_at timestamptz NOT NULL,
  closes_at timestamptz
);

CREATE TABLE survey_option (
  id uuid PRIMARY KEY,
  event_survey_id uuid NOT NULL REFERENCES event_survey (id) ON DELETE CASCADE,
  label text NOT NULL,
  UNIQUE (event_survey_id, label)
);

CREATE TABLE survey_response (
  id uuid PRIMARY KEY,
  event_survey_id uuid NOT NULL REFERENCES event_survey (id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  answer text NOT NULL,
  responded_at timestamptz NOT NULL,
  UNIQUE (event_survey_id, user_id)
);

CREATE TABLE interest_poll (
  id uuid PRIMARY KEY,
  organization_id uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  question text NOT NULL,
  public_slug text NOT NULL UNIQUE,
  opens_at timestamptz NOT NULL,
  closes_at timestamptz NOT NULL,
  CONSTRAINT interest_poll_closes_after_opening CHECK (closes_at > opens_at)
);

CREATE TABLE poll_option (
  id uuid PRIMARY KEY,
  interest_poll_id uuid NOT NULL REFERENCES interest_poll (id) ON DELETE CASCADE,
  label text NOT NULL,
  position integer NOT NULL,
  UNIQUE (interest_poll_id, label)
);

CREATE TABLE poll_response (
  id uuid PRIMARY KEY,
  interest_poll_id uuid NOT NULL REFERENCES interest_poll (id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  poll_option_id uuid NOT NULL REFERENCES poll_option (id) ON DELETE CASCADE,
  responded_at timestamptz NOT NULL,
  UNIQUE (interest_poll_id, user_id)
);

-- Core: audit ---------------------------------------------------------------------------------

CREATE TABLE audit_log_entry (
  id uuid PRIMARY KEY,
  actor_id uuid REFERENCES app_user (id) ON DELETE SET NULL,
  event_id uuid REFERENCES event (id) ON DELETE CASCADE,
  action text NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  details jsonb NOT NULL,
  occurred_at timestamptz NOT NULL
);
CREATE INDEX audit_log_entry_event_id_idx ON audit_log_entry (event_id);

-- Notifications ---------------------------------------------------------------------------------
-- Foreign keys to Core's tables let Core delete a user or an event: the cascade
-- runs with the rights of this table's owner, not Core's.

CREATE TABLE notification (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  type notification_type NOT NULL,
  -- The EventChange, Alert or Incident behind it; no foreign key (ADR 0002).
  cause_id uuid,
  title text NOT NULL,
  body text NOT NULL,
  deduplication_key text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL,
  delivered_at timestamptz,
  read_at timestamptz
);
CREATE INDEX notification_user_id_created_at_idx ON notification (user_id, created_at DESC);

CREATE TABLE user_notification_setting (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  type notification_type NOT NULL,
  enabled boolean NOT NULL,
  UNIQUE (user_id, type)
);

CREATE TABLE event_notification_setting (
  id uuid PRIMARY KEY,
  event_connection_id uuid NOT NULL REFERENCES event_connection (id) ON DELETE CASCADE,
  type notification_type NOT NULL,
  enabled boolean NOT NULL,
  UNIQUE (event_connection_id, type)
);
