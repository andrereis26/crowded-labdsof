#!/usr/bin/env bash
# Shows what runs locally: the services of the stack (stack-up.sh) and
# the gateway, the infrastructure (infra-up.sh), and the apps that start.sh
# runs from source. A Swarm task counts as running only once its health check
# passes, so "1/1" replicas means healthy.
# Usage: scripts/local/status.sh
# Windows twin: scripts/local/status.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

case "${1:-}" in
  "") ;;
  -h | --help)
    echo "Usage: scripts/local/status.sh"
    exit 0
    ;;
  *) die "unknown option: $1 (status.sh takes none)" ;;
esac

load_env
require_docker

echo "Stack (stack-up.sh):"
if stack_exists; then
  docker stack services "${STACK_NAME}" --format 'table {{.Name}}\t{{.Mode}}\t{{.Replicas}}\t{{.Image}}'
  # Tasks that should run but do not, with Swarm's reason.
  docker stack ps "${STACK_NAME}" --filter desired-state=running \
    --format '{{.Name}}\t{{.CurrentState}}\t{{.Error}}' | grep -v $'\tRunning' || true
  printf 'Gateway: %s\n' "$(docker ps --all --filter "name=^${GATEWAY}\$" --format '{{.Status}}' | grep . || echo 'not running')"
else
  echo "  not running"
fi

echo
echo "Infrastructure (infra-up.sh):"
if infra_running; then
  infra_compose ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'
else
  echo "  not running"
fi

echo
echo "Apps from source (start.sh):"
for app in ${APPS}; do
  port="$(app_port "${app}")"
  if pid="$(app_pid "${app}")"; then
    state="running, process ${pid}"
    port_open "${port}" && state="${state}, http://localhost:${port}/" || state="${state}, starting"
  else
    state="stopped"
  fi
  printf '  %-16s %s\n' "${app}" "${state}"
done
