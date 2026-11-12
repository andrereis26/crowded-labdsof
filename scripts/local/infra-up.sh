#!/usr/bin/env bash
# Starts the infrastructure alone, for apps run from source by start.sh:
# PostgreSQL migrated and seeded (database/compose.yaml), RabbitMQ and
# Redis, in containers, with the passwords of .env and their ports on
# 127.0.0.1. Running it again keeps what runs and migrates again (a no-op).
# The database lives in memory: infra-down.sh, or a restart, empties it.
# Usage: scripts/local/infra-up.sh
# Windows twin: scripts/local/infra-up.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

case "${1:-}" in
  "") ;;
  -h | --help)
    echo "Usage: scripts/local/infra-up.sh"
    exit 0
    ;;
  *) die "unknown option: $1 (infra-up.sh takes none)" ;;
esac

load_env
require_docker
if stack_exists; then
  die "the stack of stack-up.sh uses the same ports: run scripts/local/stack-down.sh first."
fi

log "Starting PostgreSQL, RabbitMQ and Redis"
infra_compose up --detach --wait postgres rabbitmq redis
log "Migrating the database and loading the demo data"
infra_compose build --quiet migrate
infra_compose run --rm migrate

cat <<EOF

The infrastructure is running, on 127.0.0.1 only:
  PostgreSQL  127.0.0.1:${DB_PORT}, database ${POSTGRES_DB} (users and passwords in .env)
  RabbitMQ    127.0.0.1:${RABBITMQ_PORT}
  Redis       127.0.0.1:${REDIS_PORT}
Run an app with scripts/local/start.sh <$(printf '%s' "${APPS}" | tr ' ' '|')>.
EOF
