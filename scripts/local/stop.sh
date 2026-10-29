#!/usr/bin/env bash
# Stops an app that start.sh runs from source, with every process it started
# (#111): SIGTERM first, so Spring Boot shuts down gracefully, then SIGKILL
# after STOP_TIMEOUT_SECONDS. An app that does not run is left as it is.
# Usage: scripts/local/stop.sh <core|jobs|notifications|backoffice|participant-app>
# Windows twin: scripts/local/stop.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

usage="Usage: scripts/local/stop.sh <$(printf '%s' "${APPS}" | tr ' ' '|')>"
app="${1:-}"
case "${app}" in -h | --help)
  echo "${usage}"
  exit 0
  ;;
esac
is_app "${app}" && [[ $# -eq 1 ]] || die "${usage}"
STOP_TIMEOUT_SECONDS="${STOP_TIMEOUT_SECONDS:-30}"

if ! pid="$(app_pid "${app}")"; then
  rm -f "$(pid_file "${app}")"
  log "${app} does not run."
  exit 0
fi

# start.sh made the app the leader of its own process group: signal the group.
log "Stopping ${app} (process ${pid})"
kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}"
deadline=$(($(date +%s) + STOP_TIMEOUT_SECONDS))
while kill -0 -- "-${pid}" 2>/dev/null; do
  if (($(date +%s) >= deadline)); then
    log "${app} is still running after ${STOP_TIMEOUT_SECONDS}s: killing it."
    kill -KILL -- "-${pid}" 2>/dev/null || true
    break
  fi
  sleep 1
done
rm -f "$(pid_file "${app}")"
log "${app} stopped."
