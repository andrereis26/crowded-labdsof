-- Flyway callback: runs before every migrate, so the service users exist
-- before the grants in R__grants.sql, and a password changed in Doppler takes
-- effect at the next deployment. The passwords are placeholders that the
-- entrypoint fills from the environment (letters and digits only), never
-- stored in the repository.
--
-- The users can log in and nothing else: they own no schema and cannot create
-- one, so only the migration user changes the schema (ADR 0012).
DO $$
DECLARE
  name text;
BEGIN
  FOREACH name IN ARRAY ARRAY['crowded_core', 'crowded_jobs', 'crowded_notifications'] LOOP
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = name) THEN
      EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT', name);
    END IF;
  END LOOP;
END
$$;

ALTER ROLE crowded_core WITH LOGIN PASSWORD '${core_password}';
ALTER ROLE crowded_jobs WITH LOGIN PASSWORD '${jobs_password}';
ALTER ROLE crowded_notifications WITH LOGIN PASSWORD '${notifications_password}';
