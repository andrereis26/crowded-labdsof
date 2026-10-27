# Functions shared by remote-deploy.sh and remote-rollback.sh. Sourced, not run.
# shellcheck shell=bash

STACK_NAME="${STACK_NAME:-crowded}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/crowded}"
RELEASES_DIR="${DEPLOY_ROOT}/releases"
CURRENT_LINK="${DEPLOY_ROOT}/current"
STACK_FILE_NAME="docker-compose.swarm.yml"
# Releases (and the secrets they reference) kept for rollback.
KEEP_RELEASES="${KEEP_RELEASES:-5}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-600}"
# The participant app through the router: proves the router and its routing.
SMOKE_URL="${SMOKE_URL:-http://127.0.0.1/}"

# Application services: their image comes from a release; the rest of the
# stack (data stores, router) is pinned in the stack file.
APP_SERVICES=(core jobs notifications backoffice participant-app)

# Messages go to stderr, so functions can print their result on stdout.
log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

require_command() {
  type -P "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

# Runs a command as root, through sudo when the script is not root.
as_root() {
  if [[ "${EUID}" -eq 0 ]]; then "$@"; else sudo "$@"; fi
}

# Docker through sudo until the user's docker group membership is active.
# sudo runs the docker binary, not this function, on purpose.
# shellcheck disable=SC2032,SC2033
docker() {
  if [[ "${DOCKER_NEEDS_SUDO:-false}" == "true" ]]; then
    sudo docker "$@"
  else
    command docker "$@"
  fi
}

# shellcheck disable=SC2033
detect_docker_access() {
  if command docker info >/dev/null 2>&1; then
    DOCKER_NEEDS_SUDO=false
  elif sudo -n docker info >/dev/null 2>&1; then
    DOCKER_NEEDS_SUDO=true
  else
    die "cannot reach the Docker daemon, even through sudo"
  fi
}

# core -> CORE_IMAGE, participant-app -> PARTICIPANT_APP_IMAGE
image_var() {
  local name="${1//-/_}"
  printf '%s_IMAGE' "${name^^}"
}

# Successful releases, oldest first. Failed releases end in .failed.
list_releases() {
  [[ -d "${RELEASES_DIR}" ]] || return 0
  find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d ! -name '*.failed' -printf '%f\n' | sort
}

current_release() {
  [[ -L "${CURRENT_LINK}" ]] && basename "$(readlink -f "${CURRENT_LINK}")"
}

# Deploys the stack exactly as recorded in a release directory.
deploy_release() {
  local release_dir="$1"
  (
    set -a
    # shellcheck source=/dev/null
    source "${release_dir}/release.env"
    set +a
    # Callers run this inside `if`, where set -e does not apply: chain explicitly.
    cd "${release_dir}" \
      && docker stack config --compose-file "${STACK_FILE_NAME}" >/dev/null \
      && docker stack deploy --compose-file "${STACK_FILE_NAME}" --prune --detach=false "${STACK_NAME}"
  )
}

# The image digest an application service should run in a release.
expected_digest() {
  local release_dir="$1" service="$2" var
  var="$(image_var "${service}")"
  sed -n "s/^${var}=.*@\(sha256:[0-9a-f]*\).*/\1/p" "${release_dir}/release.env"
}

# Waits until every service runs all its replicas and every application service
# runs the image of the release. Swarm rolls a service back by itself when its
# update fails (update_config.failure_action), which shows here as a wrong image.
wait_for_release() {
  local release_dir="$1" deadline pending service replicas running desired image expected
  deadline=$(( $(date +%s) + WAIT_TIMEOUT_SECONDS ))

  while true; do
    pending=()
    while read -r service replicas; do
      # "2/2" or "1/1 (max 1 per node)": running equals desired, and not 0/0.
      running="${replicas%%/*}"
      desired="${replicas#*/}"
      desired="${desired%% *}"
      [[ "${running}" == "${desired}" && "${desired}" != "0" ]] || pending+=("${service} ${replicas}")
    done < <(docker stack services "${STACK_NAME}" --format '{{.Name}} {{.Replicas}}')

    for service in "${APP_SERVICES[@]}"; do
      expected="$(expected_digest "${release_dir}" "${service}")"
      image="$(docker service inspect "${STACK_NAME}_${service}" \
        --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}' 2>/dev/null || true)"
      [[ -n "${expected}" && "${image}" == *"${expected}"* ]] \
        || pending+=("${service} image ${image:-missing}")
    done

    if (( ${#pending[@]} == 0 )); then
      return 0
    fi
    if (( $(date +%s) >= deadline )); then
      log "Timed out after ${WAIT_TIMEOUT_SECONDS}s. Not ready:"
      printf '  %s\n' "${pending[@]}" >&2
      return 1
    fi
    sleep 5
  done
}

smoke_test() {
  local attempt
  for attempt in $(seq 1 12); do
    if curl --fail --silent --show-error --max-time 5 "${SMOKE_URL}" >/dev/null; then
      return 0
    fi
    sleep 5
  done
  log "Smoke test failed after ${attempt} attempts: ${SMOKE_URL}"
  return 1
}

print_service_states() {
  docker stack ps "${STACK_NAME}" --no-trunc \
    --format '{{.Name}} {{.CurrentState}} {{.Error}}' --filter desired-state=running >&2 || true
}

mark_current() {
  ln -sfn "${RELEASES_DIR}/$1" "${CURRENT_LINK}"
}
