#!/usr/bin/env bash
# Rolls Crowded back to an earlier release on this host: deploys that release's
# stack file, images (by digest) and secrets exactly as they were recorded, so
# every service goes back together. Needs no Doppler token: the secrets of the
# kept releases are still in the Swarm.
#
# Usage:  remote-rollback.sh            the release before the current one
#         remote-rollback.sh <release>  a release listed by --list
#         remote-rollback.sh --list     the releases that can be restored
#
# Environment: STACK_NAME, DEPLOY_ROOT, WAIT_TIMEOUT_SECONDS, SMOKE_URL
# (optional, see lib/common.sh).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source-path=SCRIPTDIR source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

print_releases() {
  local current release
  current="$(current_release || true)"
  while read -r release; do
    if [[ "${release}" == "${current}" ]]; then
      printf '%s (current)\n' "${release}"
    else
      printf '%s\n' "${release}"
    fi
  done < <(list_releases)
}

# The newest release older than the current one.
release_before_current() {
  local current release previous=""
  current="$(current_release || true)"
  [[ -n "${current}" ]] || die "no current release recorded in ${CURRENT_LINK}"
  while read -r release; do
    [[ "${release}" == "${current}" ]] && break
    previous="${release}"
  done < <(list_releases)
  printf '%s' "${previous}"
}

main() {
  require_command curl
  detect_docker_access

  if [[ "${1:-}" == "--list" ]]; then
    print_releases
    return 0
  fi

  local target="${1:-}"
  [[ -n "${target}" ]] || target="$(release_before_current)"
  [[ -n "${target}" ]] || die "there is no release before the current one"
  [[ "${target}" != */* && -f "${RELEASES_DIR}/${target}/release.env" ]] \
    || die "unknown release '${target}'; see --list"

  log "Rolling back from $(current_release || echo none) to ${target}"
  if deploy_release "${RELEASES_DIR}/${target}" \
    && wait_for_release "${RELEASES_DIR}/${target}" && smoke_test; then
    mark_current "${target}"
    log "Release ${target} is live."
    return 0
  fi

  print_service_states
  die "the rollback to ${target} did not finish; check the services by hand"
}

main "$@"
