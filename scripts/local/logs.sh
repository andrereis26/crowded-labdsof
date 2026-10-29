#!/usr/bin/env bash
# Follows the log of one service (#111), the first that runs of:
#   a service of the stack (stack-up.sh), e.g. core, database, postgres;
#   the stack's gateway: gateway;
#   a service of the infrastructure (infra-up.sh): postgres, migrate, rabbitmq, redis;
#   an app that start.sh runs from source.
# Stop following with Ctrl+C.
# Usage: scripts/local/logs.sh <service>
# Windows twin: scripts/local/logs.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

service="${1:-}"
case "${service}" in
  -h | --help)
    echo "Usage: scripts/local/logs.sh <service>"
    exit 0
    ;;
esac
[[ -n "${service}" && $# -eq 1 ]] || die "Usage: scripts/local/logs.sh <service>"

load_env
require_docker

if stack_exists && docker service inspect "${STACK_NAME}_${service}" >/dev/null 2>&1; then
  exec docker service logs --follow --tail 100 "${STACK_NAME}_${service}"
fi
if [[ "${service}" == gateway ]] && docker container inspect "${GATEWAY}" >/dev/null 2>&1; then
  exec docker logs --follow --tail 100 "${GATEWAY}"
fi
if infra_running && infra_compose ps --all --services | grep -qx "${service}"; then
  infra_compose logs --follow --tail 100 "${service}"
  exit
fi
if is_app "${service}" && [[ -f "$(log_file "${service}")" ]]; then
  app_pid "${service}" >/dev/null || log "${service} does not run; its last log:"
  exec tail -n 100 -f "$(log_file "${service}")"
fi
die "no running service named ${service}: see scripts/local/status.sh."
