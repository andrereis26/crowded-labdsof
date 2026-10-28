#!/bin/sh
# Runs Flyway against the Crowded database (ADR 0012). Configured by the
# environment, the same way in Swarm, in the integration tests and locally:
#
#   DB_URL                     JDBC URL, e.g. jdbc:postgresql://postgres:5432/crowded
#   DB_USERNAME, DB_PASSWORD   the migration user, which owns the schema
#   CORE_DB_PASSWORD, JOBS_DB_PASSWORD, NOTIFICATIONS_DB_PASSWORD
#                              passwords of the service users (crowded_core, ...)
#   SEED_DEMO_DATA             "true" also applies the demo data in seed/; default false
#
# Each password can instead be read from a file named by <NAME>_FILE, such as
# a Swarm secret: DB_PASSWORD_FILE=/run/secrets/postgres_password.
# Arguments are Flyway's: `migrate` (the default), `info`, `validate`...

set -eu

die() { echo "Error: $*" >&2; exit 1; }

# Sets NAME from NAME_FILE when that is given.
from_file() {
  file="$(eval "printf '%s' \"\${$1_FILE:-}\"")"
  [ -n "${file}" ] || return 0
  [ -r "${file}" ] || die "$1_FILE: cannot read ${file}"
  eval "$1=\"\$(cat \"\${file}\")\""
}

for name in DB_PASSWORD CORE_DB_PASSWORD JOBS_DB_PASSWORD NOTIFICATIONS_DB_PASSWORD; do
  from_file "${name}"
done

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

# The service users' passwords are written into an ALTER ROLE statement by
# the beforeMigrate callback, so they may only hold letters and digits.
for name in CORE_DB_PASSWORD JOBS_DB_PASSWORD NOTIFICATIONS_DB_PASSWORD; do
  value="$(eval "printf '%s' \"\${${name}:-}\"")"
  [ -n "${value}" ] || die "${name} is required"
  case "${value}" in
    *[!A-Za-z0-9]*) die "${name} may only contain letters and digits" ;;
  esac
done

locations="filesystem:/flyway/migrations"
case "${SEED_DEMO_DATA:-false}" in
  true) locations="${locations},filesystem:/flyway/seed" ;;
  false) ;;
  *) die "SEED_DEMO_DATA must be true or false" ;;
esac

export FLYWAY_URL="${DB_URL}"
export FLYWAY_USER="${DB_USERNAME}"
export FLYWAY_PASSWORD="${DB_PASSWORD}"
export FLYWAY_LOCATIONS="${locations}"
export FLYWAY_PLACEHOLDERS_CORE_PASSWORD="${CORE_DB_PASSWORD}"
export FLYWAY_PLACEHOLDERS_JOBS_PASSWORD="${JOBS_DB_PASSWORD}"
export FLYWAY_PLACEHOLDERS_NOTIFICATIONS_PASSWORD="${NOTIFICATIONS_DB_PASSWORD}"
# PostgreSQL may still be starting.
export FLYWAY_CONNECT_RETRIES="${FLYWAY_CONNECT_RETRIES:-30}"
export FLYWAY_VALIDATE_MIGRATION_NAMING=true
export FLYWAY_FAIL_ON_MISSING_LOCATIONS=true
# A rollback runs an older image against a newer schema: its unknown (future)
# migrations are fine. So is a database seeded earlier, migrated without the
# seed now.
export FLYWAY_IGNORE_MIGRATION_PATTERNS="*:future,repeatable:missing"

[ "$#" -gt 0 ] || set -- migrate
exec flyway "$@"
