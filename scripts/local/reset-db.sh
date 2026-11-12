#!/usr/bin/env bash
# Drops the local database and creates it again, migrated and with the demo
# data when SEED_DEMO_DATA is true, in the stack of stack-up.sh, or else
# in the infrastructure of infra-up.sh. The services' open connections are
# closed; their connection pools open new ones.
# Usage: scripts/local/reset-db.sh
# Windows twin: scripts/local/reset-db.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

case "${1:-}" in
  "") ;;
  -h | --help)
    echo "Usage: scripts/local/reset-db.sh"
    exit 0
    ;;
  *) die "unknown option: $1 (reset-db.sh takes none)" ;;
esac

# Runs in the PostgreSQL container, as the user that owns the database, over
# the local socket, which needs no password.
# shellcheck disable=SC2016 # expanded by the container's shell
RECREATE='psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d postgres \
  -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE)" \
  -c "CREATE DATABASE \"$POSTGRES_DB\""'

load_env
require_docker

if stack_exists; then
  container="$(docker ps --quiet --filter "label=com.docker.swarm.service.name=${STACK_NAME}_postgres" | head -n 1)"
  [[ -n "${container}" ]] || die "the stack's PostgreSQL does not run: see scripts/local/status.sh."
  log "Dropping and creating the database ${POSTGRES_DB}"
  docker exec "${container}" sh -c "${RECREATE}"
  log "Migrating it (the stack's database job)"
  docker service update --force --detach "${STACK_NAME}_database" >/dev/null
  wait_for_job "${STACK_NAME}_database"
elif infra_running; then
  log "Dropping and creating the database ${POSTGRES_DB}"
  infra_compose exec -T postgres sh -c "${RECREATE}"
  log "Migrating it"
  infra_compose run --rm migrate
else
  die "neither the stack (stack-up.sh) nor the infrastructure (infra-up.sh) runs."
fi
log "The database is new."
