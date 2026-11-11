#!/usr/bin/env bash
# Tests a crowded-database image (ADR 0012) against throwaway PostgreSQL
# databases, the way a deployment runs it:
#   1. without the seed: migrates an empty database, a second run changes
#      nothing, no demo data, and each service user writes only its own tables
#      and cannot change the schema;
#   2. with the seed: the demo data is there, its passwords are hashed, and a
#      second run changes nothing either.
# Usage: scripts/ci/test-database.sh <image>

set -euo pipefail

IMAGE="${1:?usage: test-database.sh <image>}"
RUN_ID="dbtest-$$"
NETWORK="${RUN_ID}"
OWNER_PASSWORD="$(openssl rand -hex 16)"
CORE_PASSWORD="$(openssl rand -hex 16)"
JOBS_PASSWORD="$(openssl rand -hex 16)"
NOTIFICATIONS_PASSWORD="$(openssl rand -hex 16)"

# shellcheck disable=SC2329 # called by the trap below
cleanup() {
  docker rm --force --volumes "${RUN_ID}-plain" "${RUN_ID}-seeded" >/dev/null 2>&1 || true
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "ok: $*"; }

start_postgres() {
  docker run --detach --name "$1" --network "${NETWORK}" --tmpfs /var/lib/postgresql \
    -e POSTGRES_DB=crowded -e POSTGRES_USER=crowded -e POSTGRES_PASSWORD="${OWNER_PASSWORD}" \
    postgres:18-alpine >/dev/null
}

# migrate <postgres container> <seed true|false>
migrate() {
  docker run --rm --network "${NETWORK}" \
    -e DB_URL="jdbc:postgresql://$1:5432/crowded" -e DB_USERNAME=crowded -e DB_PASSWORD="${OWNER_PASSWORD}" \
    -e CORE_DB_PASSWORD="${CORE_PASSWORD}" -e JOBS_DB_PASSWORD="${JOBS_PASSWORD}" \
    -e NOTIFICATIONS_DB_PASSWORD="${NOTIFICATIONS_PASSWORD}" -e SEED_DEMO_DATA="$2" \
    "${IMAGE}" migrate
}

# sql <postgres container> <user> <password> <statement>: prints the result,
# or the error on stdout with a non-zero status.
sql() {
  docker exec -e PGPASSWORD="$3" "$1" \
    psql -h 127.0.0.1 -U "$2" -d crowded -v ON_ERROR_STOP=1 -tAc "$4" 2>&1
}

# A fixed restrict key: pg_dump picks a random one otherwise.
dump() {
  docker exec -e PGPASSWORD="${OWNER_PASSWORD}" "$1" pg_dump -h 127.0.0.1 -U crowded -d crowded --restrict-key=compare
}

# Migrating again must change nothing: same schema, same data.
check_second_run() {
  local before
  before="$(dump "$1")"
  migrate "$1" "$2" >/dev/null
  [[ "$(dump "$1")" == "${before}" ]] || fail "a second run (seed $2) changed the database"
  pass "a second run (seed $2) changes nothing"
}

expect_ok() {
  sql "$1" "$2" "$3" "$4" >/dev/null || fail "$2 could not run: $4"
}

expect_denied() {
  local output
  if output="$(sql "$1" "$2" "$3" "$4")"; then
    fail "$2 could run: $4"
  fi
  grep -qE 'permission denied|must be owner' <<<"${output}" || fail "$2: unexpected error: ${output}"
}

docker network create "${NETWORK}" >/dev/null
start_postgres "${RUN_ID}-plain"
start_postgres "${RUN_ID}-seeded"

# 1. Without the seed ----------------------------------------------------------

db="${RUN_ID}-plain"
migrate "${db}" false
pass "migrations apply to an empty database"
check_second_run "${db}" false

[[ "$(sql "${db}" crowded "${OWNER_PASSWORD}" 'SELECT count(*) FROM app_user')" == 0 ]] \
  || fail "demo data was applied without the seed"
pass "no demo data without the seed"

for user in crowded_core:"${CORE_PASSWORD}" crowded_jobs:"${JOBS_PASSWORD}" \
  crowded_notifications:"${NOTIFICATIONS_PASSWORD}"; do
  name="${user%%:*}" password="${user#*:}"
  expect_ok "${db}" "${name}" "${password}" 'SELECT count(*) FROM event, notification, report_category'
  expect_denied "${db}" "${name}" "${password}" "CREATE TABLE ${name}_test (id integer)"
  expect_denied "${db}" "${name}" "${password}" 'ALTER TABLE event ADD COLUMN test integer'
  expect_denied "${db}" "${name}" "${password}" 'DROP TABLE notification'
done
pass "every service user reads every table and cannot change the schema"

core_write="INSERT INTO organization (id, name, created_at) VALUES (gen_random_uuid(), 'Test', now())"
notifications_write="DELETE FROM user_notification_setting"
expect_ok "${db}" crowded_core "${CORE_PASSWORD}" "${core_write}"
expect_denied "${db}" crowded_core "${CORE_PASSWORD}" "${notifications_write}"
expect_ok "${db}" crowded_notifications "${NOTIFICATIONS_PASSWORD}" "${notifications_write}"
expect_denied "${db}" crowded_notifications "${NOTIFICATIONS_PASSWORD}" "${core_write}"
expect_denied "${db}" crowded_jobs "${JOBS_PASSWORD}" "${core_write}"
expect_denied "${db}" crowded_jobs "${JOBS_PASSWORD}" "${notifications_write}"
pass "each service user writes only the tables it owns"

# 2. With the seed ---------------------------------------------------------------

db="${RUN_ID}-seeded"
migrate "${db}" true
pass "migrations and demo data apply to an empty database"
check_second_run "${db}" true

[[ "$(sql "${db}" crowded "${OWNER_PASSWORD}" 'SELECT count(DISTINCT status) FROM event')" == 6 ]] \
  || fail "the demo data has no event in every state"
pass "the demo data has an event in every state"

# The refined scope (#142): both kinds of event and of connection, and a weather warning.
[[ "$(sql "${db}" crowded "${OWNER_PASSWORD}" "SELECT
    (SELECT count(DISTINCT type) FROM event) = 2
    AND EXISTS (SELECT FROM ticket) AND EXISTS (SELECT FROM access_code_connection)
    AND EXISTS (SELECT FROM weather_warning)")" == t ]] \
  || fail "the demo data lacks a kind of event, a kind of connection or a weather warning"
pass "the demo data has ticketed and public events, both kinds of connection and a weather warning"

# {bcrypt} and a 60-character bcrypt hash.
[[ "$(sql "${db}" crowded "${OWNER_PASSWORD}" \
  "SELECT count(*) FROM app_user WHERE password_hash NOT LIKE '{bcrypt}\$2a\$10\$%' OR length(password_hash) <> 68")" == 0 ]] \
  || fail "a demo password is not a bcrypt hash"
pass "demo passwords are stored as bcrypt hashes"

echo "${IMAGE} passed."
