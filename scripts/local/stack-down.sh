#!/usr/bin/env bash
# Removes what stack-up.sh created: the gateway, the stack with its
# networks, its volumes (the data) and its secrets. Swarm stays on; turn it off
# with docker swarm leave --force. Running it again finds nothing to remove.
# Usage: scripts/local/stack-down.sh
# Windows twin: scripts/local/stack-down.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

case "${1:-}" in
  "") ;;
  -h | --help)
    echo "Usage: scripts/local/stack-down.sh"
    exit 0
    ;;
  *) die "unknown option: $1 (stack-down.sh takes none)" ;;
esac
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"

require_docker

# First, or the stack's networks cannot be removed.
if docker container inspect "${GATEWAY}" >/dev/null 2>&1; then
  log "Removing the gateway"
  docker rm --force "${GATEWAY}" >/dev/null
fi

if stack_exists; then
  log "Removing the stack"
  docker stack rm "${STACK_NAME}" >/dev/null
fi

# docker stack rm returns before the containers and networks are gone, and a
# volume cannot be removed while a container uses it.
label="label=com.docker.stack.namespace=${STACK_NAME}"
deadline=$(($(date +%s) + TIMEOUT_SECONDS))
while [[ -n "$(docker ps --all --quiet --filter "${label}")$(docker network ls --quiet --filter "${label}")" ]]; do
  (($(date +%s) < deadline)) || die "the stack's containers or networks are still there after ${TIMEOUT_SECONDS}s."
  sleep 2
done

volumes="$(docker volume ls --quiet --filter "${label}")"
if [[ -n "${volumes}" ]]; then
  log "Removing the data"
  # shellcheck disable=SC2086 # one volume per word
  docker volume rm ${volumes} >/dev/null
fi

if swarm_active; then
  secrets="$(docker secret ls --quiet --filter "label=${LOCAL_LABEL}")"
  if [[ -n "${secrets}" ]]; then
    log "Removing the secrets"
    # shellcheck disable=SC2086 # one secret per word
    docker secret rm ${secrets} >/dev/null
  fi
fi
log "The local stack is gone."
