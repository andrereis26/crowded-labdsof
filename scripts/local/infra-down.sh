#!/usr/bin/env bash
# Stops the infrastructure of infra-up.sh and deletes its data. Apps
# still running from source lose their connections: stop them with stop.sh.
# Usage: scripts/local/infra-down.sh
# Windows twin: scripts/local/infra-down.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

case "${1:-}" in
  "") ;;
  -h | --help)
    echo "Usage: scripts/local/infra-down.sh"
    exit 0
    ;;
  *) die "unknown option: $1 (infra-down.sh takes none)" ;;
esac

load_env
require_docker
log "Stopping the infrastructure"
infra_compose down --volumes --remove-orphans
