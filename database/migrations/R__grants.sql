-- Table ownership (ADR 0002, data model): every service reads every table and
-- writes only the tables it owns. Repeatable, so Flyway runs it again whenever
-- it changes: a new table gets its writer here, in the same pull request.
-- The migration user owns the schema and every table; the service users own
-- nothing, so any schema change by a service fails.

GRANT CONNECT ON DATABASE "${flyway:database}" TO crowded_core, crowded_jobs, crowded_notifications;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO crowded_core, crowded_jobs, crowded_notifications;

-- Reads: every table, including the ones later migrations create.
GRANT SELECT ON ALL TABLES IN SCHEMA public TO crowded_core, crowded_jobs, crowded_notifications;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT ON TABLES TO crowded_core, crowded_jobs, crowded_notifications;

-- Writes: start from none, so this file is the whole list.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA public
  FROM crowded_core, crowded_jobs, crowded_notifications;

-- Core: users, organizations, events, connections, on-site information,
-- alerts, surveys, reports, incidents, the audit log and its outbox.
GRANT INSERT, UPDATE, DELETE ON
  app_user, organization,
  event, event_change,
  event_connection, ticket, access_code_connection, ticket_extraction,
  queue_time, weather_forecast, weather_warning,
  alert, alert_draft, event_survey, survey_option, survey_response,
  report, report_category, event_category_setting, confirmation, incident, recognition,
  audit_log_entry, outbox
  TO crowded_core;

-- Jobs: its sync snapshots, when a migration adds them. It owns no table of
-- the data model yet; it publishes what it detects and Core applies it.

-- Notifications: notifications and preferences.
GRANT INSERT, UPDATE, DELETE ON
  notification, user_notification_setting, event_notification_setting
  TO crowded_notifications;
