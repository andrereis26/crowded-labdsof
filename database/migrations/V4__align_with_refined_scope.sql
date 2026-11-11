-- Aligns the schema with the refined MVP scope (#136, ADR 0015) and the data
-- model (docs/deliverables/diagrams/data-model.puml), #142.
--
-- Out: interest polls, ownership requests, organization verification, API
-- credentials, the ticketing platform and municipal agenda as event sources,
-- proposed events, follows, AI match candidates, ticket confirmation and
-- corroborated changes. In: the event's type, its event or access code and
-- district, access-code connections, the ticket's keyed entry-code hash and
-- match method, the members' account fields, the forecast's range and IPMA
-- weather warnings.
--
-- The databases that exist when this runs hold only demo data. What the new
-- model can hold is converted; what it cannot is deleted, and the seed adds
-- the new demo rows. On an empty database the conversions change nothing.
--
-- No release before this one writes or maps a dropped column, so dropping them
-- now keeps the previous release working (README, rule 4).

-- Out of scope ---------------------------------------------------------------------

DROP TABLE poll_response, poll_option, interest_poll;
DROP TABLE ownership_request;
DROP TYPE request_decision;
DROP TABLE api_credential;
DROP TABLE event_match_candidate;

-- A follow has no place in the model: a participant connects with a ticket or
-- an access code. Its settings go with the connection.
DELETE FROM event_connection WHERE id IN (SELECT id FROM follow);
DROP TABLE follow;

-- Accounts and organizations ------------------------------------------------------------

-- Members are created by an administrator with a temporary password that
-- expires after 7 days and allows only setting a new one (FR-087, FR-088,
-- SR-022). A deactivated member cannot sign in (SR-001).
ALTER TABLE app_user
  ADD COLUMN active boolean NOT NULL DEFAULT true,
  ADD COLUMN must_change_password boolean NOT NULL DEFAULT false,
  ADD COLUMN temporary_password_expires_at timestamptz,
  ADD CONSTRAINT app_user_member_has_organization
    CHECK ((role = 'ORGANIZATION_MEMBER') = (organization_id IS NOT NULL)),
  ADD CONSTRAINT app_user_temporary_password_expires
    CHECK (NOT must_change_password OR temporary_password_expires_at IS NOT NULL);

-- Organizations are created by an administrator, not verified (FR-087). The
-- creator is kept when the administrator's account is deleted.
ALTER TABLE organization
  ADD COLUMN created_by_user_id uuid REFERENCES app_user (id) ON DELETE SET NULL,
  ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
UPDATE organization SET
  created_by_user_id = coalesce(
    verified_by_user_id,
    (SELECT id FROM app_user WHERE role = 'ADMINISTRATOR' ORDER BY created_at, id LIMIT 1)),
  created_at = coalesce(verified_at, created_at);
-- A rejected organization was never allowed in; its members stay, deactivated.
UPDATE app_user SET active = false
  WHERE organization_id IN (SELECT id FROM organization WHERE verification_status = 'REJECTED');
ALTER TABLE organization
  ALTER COLUMN created_at DROP DEFAULT,
  DROP COLUMN verified_by_user_id,
  DROP COLUMN verification_status,
  DROP COLUMN verified_at;
DROP TYPE verification_status;

-- Events -----------------------------------------------------------------------------------

-- Every event belongs to an organization and has a schedule and a venue
-- (FR-015). Proposed events and those from other sources go, with their
-- changes, connections, reports and alerts.
DELETE FROM event
  WHERE status = 'PROPOSED' OR organization_id IS NULL
    OR start_date_time IS NULL OR end_date_time IS NULL
    OR venue_name IS NULL OR address IS NULL OR latitude IS NULL OR longitude IS NULL;

ALTER TABLE event DROP CONSTRAINT event_start_known;
ALTER TYPE event_status RENAME TO event_status_v1;
CREATE TYPE event_status AS ENUM ('SCHEDULED', 'POSTPONED', 'RELOCATED', 'CANCELLED', 'ONGOING', 'ENDED');
ALTER TABLE event ALTER COLUMN status TYPE event_status USING status::text::event_status;
DROP TYPE event_status_v1;

-- The visibility becomes the event's type: a ticketed event has the code
-- printed on its tickets, a public one the access code the organization gives
-- out (FR-015). Each code is unique among upcoming events, which Core checks
-- (FR-086). The district selects the IPMA warnings (FR-036).
ALTER TYPE event_visibility RENAME TO event_type;
ALTER TABLE event RENAME COLUMN visibility TO type;
ALTER TABLE event
  ADD COLUMN ticket_event_code text,
  ADD COLUMN access_code text,
  ADD COLUMN district text;
-- Existing demo events get a code from the end of their id, which differs
-- between them, and are all in Porto.
UPDATE event SET
  ticket_event_code = CASE WHEN type = 'TICKETED'
    THEN 'EV-' || upper(right(replace(id::text, '-', ''), 10)) END,
  access_code = CASE WHEN type = 'PUBLIC'
    THEN 'EV' || upper(right(replace(id::text, '-', ''), 10)) END,
  district = 'Porto';

ALTER TABLE event
  DROP COLUMN tentative_period,
  DROP COLUMN origin,
  DROP COLUMN ticketing_source_event_id,
  DROP COLUMN last_synced_at,
  ALTER COLUMN organization_id SET NOT NULL,
  ALTER COLUMN start_date_time SET NOT NULL,
  ALTER COLUMN end_date_time SET NOT NULL,
  ALTER COLUMN venue_name SET NOT NULL,
  ALTER COLUMN address SET NOT NULL,
  ALTER COLUMN district SET NOT NULL,
  ALTER COLUMN latitude SET NOT NULL,
  ALTER COLUMN longitude SET NOT NULL,
  ADD CONSTRAINT event_code_matches_type CHECK (
    (type = 'TICKETED' AND ticket_event_code IS NOT NULL AND access_code IS NULL)
    OR (type = 'PUBLIC' AND access_code IS NOT NULL AND ticket_event_code IS NULL)),
  ADD CONSTRAINT event_doors_open_before_start
    CHECK (doors_open_at IS NULL OR doors_open_at <= start_date_time);
DROP TYPE event_origin;
-- A ticket or an access code is matched to its event by the code (SR-021).
CREATE INDEX event_ticket_event_code_idx ON event (ticket_event_code);
CREATE INDEX event_access_code_idx ON event (access_code);

-- Changes are published by the owning organization only (ADR 0015): nothing
-- confirms or corroborates them. refund_info replaced refund_info_url in V3.
DELETE FROM event_change WHERE type = 'CONFIRMATION';
ALTER TYPE change_type RENAME TO change_type_v1;
CREATE TYPE change_type AS ENUM (
  'CANCELLATION', 'POSTPONEMENT', 'TIME_CHANGE', 'VENUE_CHANGE', 'DETAILS_UPDATE'
);
ALTER TABLE event_change ALTER COLUMN type TYPE change_type USING type::text::change_type;
DROP TYPE change_type_v1;
ALTER TABLE event_change
  DROP COLUMN refund_info_url,
  DROP COLUMN corroborating_source_type,
  DROP COLUMN corroborated_at;

-- Participation ------------------------------------------------------------------------------

-- A ticket is stored once it is matched, with its entry code (FR-070) and a
-- keyed hash of it that links it to one account (FR-084, SR-018). Core keys
-- the hash with the event, so one unique hash is one ticket per event. No
-- existing ticket has the hash, which only Core can compute: they go.
DELETE FROM event_connection WHERE id IN (SELECT id FROM ticket);
ALTER TABLE ticket DROP COLUMN status;
DROP TYPE ticket_status;
ALTER TYPE import_method RENAME TO import_method_v1;
CREATE TYPE import_method AS ENUM ('PDF_UPLOAD', 'IMAGE_UPLOAD');
ALTER TABLE ticket ALTER COLUMN import_method TYPE import_method
  USING import_method::text::import_method;
DROP TYPE import_method_v1;
CREATE TYPE match_method AS ENUM ('EXTRACTED_CODE', 'TYPED_CODE');
ALTER TABLE ticket
  ALTER COLUMN entry_code SET NOT NULL,
  ADD COLUMN entry_code_hash text NOT NULL,
  ADD COLUMN match_method match_method NOT NULL,
  ADD CONSTRAINT ticket_entry_code_hash_key UNIQUE (entry_code_hash);

-- The extraction keeps the event code read from the ticket (FR-004), not a
-- confidence per field.
ALTER TABLE ticket_extraction
  DROP COLUMN field_confidence,
  ADD COLUMN extracted_event_code text;

-- A participant connected to a public event with its access code (FR-085).
CREATE TABLE access_code_connection (
  id uuid PRIMARY KEY REFERENCES event_connection (id) ON DELETE CASCADE
);

-- Weather ------------------------------------------------------------------------------------

-- IPMA's daily forecast: a temperature range and the chance of rain, in
-- percent. The old rows have neither; Jobs fetches them again.
DELETE FROM weather_forecast;
ALTER TABLE weather_forecast
  DROP COLUMN temperature,
  DROP COLUMN severe_weather,
  ADD COLUMN min_temperature double precision NOT NULL,
  ADD COLUMN max_temperature double precision NOT NULL,
  ADD COLUMN precipitation_probability double precision NOT NULL
    CHECK (precipitation_probability BETWEEN 0 AND 100),
  ADD CONSTRAINT weather_forecast_range CHECK (min_temperature <= max_temperature);

-- An IPMA warning for the event's district. notified_level is the highest level
-- already notified, so each level is notified once (FR-036, NFR-003).
CREATE TYPE warning_level AS ENUM ('YELLOW', 'ORANGE', 'RED');
CREATE TABLE weather_warning (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  awareness_type text NOT NULL,
  start_time timestamptz NOT NULL,
  level warning_level NOT NULL,
  end_time timestamptz NOT NULL,
  notified_level warning_level,
  provenance_source_type source_type NOT NULL,
  provenance_source_name text NOT NULL,
  provenance_last_updated_at timestamptz NOT NULL,
  provenance_confirmed boolean NOT NULL,
  UNIQUE (event_id, awareness_type, start_time),
  CONSTRAINT weather_warning_ends_after_start CHECK (end_time >= start_time)
);

-- Notifications --------------------------------------------------------------------------------

ALTER TYPE notification_type RENAME VALUE 'SEVERE_WEATHER' TO 'WEATHER_WARNING';
