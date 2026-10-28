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
# stack (data stores, router) is pinned in the stack file. `database` is the
# one-shot migration job (ADR 0012).
# shellcheck disable=SC2034 # used by remote-deploy.sh
APP_SERVICES=(database core jobs notifications backoffice participant-app)
# The services that need the migrated database: during a deployment they keep
# their current image until the migration has completed.
DATABASE_CLIENTS=(core jobs notifications)
MIGRATION_SERVICE=database

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

# Deploys the stack exactly as recorded in a release directory, and records
# when in DEPLOY_STARTED for wait_for_release. In two steps (ADR 0012):
#   1. the release with Core, Jobs and Notifications still on the image they
#      run now (none yet on the first deployment), so the migration job runs
#      while the previous release keeps serving; then it waits for the job;
#   2. the whole release, once the database is migrated.
# A failed migration stops at step 1: the services never get the new images.
deploy_release() {
  local release_dir="$1"
  DEPLOY_STARTED="$(date +%s)"
  # Callers run this inside `if`, where set -e does not apply: chain explicitly.
  stack_deploy "${release_dir}" migration \
    && wait_for_migration "${release_dir}" \
    && stack_deploy "${release_dir}" all
}

# stack_deploy <release dir> <migration|all>
stack_deploy() {
  local release_dir="$1" step="$2"
  (
    set -a
    # shellcheck source=/dev/null
    source "${release_dir}/release.env"
    # Marks the migration job with its release, so every deployment runs it.
    # shellcheck disable=SC2034 # exported by set -a, read by the stack file
    CROWDED_RELEASE="$(basename "${release_dir}")"
    set +a

    local prune=(--prune) service var current
    if [[ "${step}" == migration ]]; then
      prune=()
      for service in "${DATABASE_CLIENTS[@]}"; do
        var="$(image_var "${service}")"
        current="$(docker service inspect --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}' \
          "${STACK_NAME}_${service}" 2>/dev/null || true)"
        if [[ -n "${current}" ]]; then
          printf -v "${var}" '%s' "${current}"
        else
          export "${service^^}_REPLICAS=0"
        fi
      done
    fi

    # Detached: the callers wait, with a timeout. `--detach=false` can keep
    # waiting after every service has converged (seen on Docker 29).
    cd "${release_dir}" \
      && docker stack config --compose-file "${STACK_FILE_NAME}" >/dev/null \
      && docker stack deploy --compose-file "${STACK_FILE_NAME}" "${prune[@]}" "${STACK_NAME}"
  )
}

# The state (complete, failed, running...) of the newest migration task that
# runs <release> and was created since <time>, or nothing while Swarm has not
# created one. Older tasks of the same release, from an earlier deployment of
# it, do not count.
migration_state() {
  local release="$1" since="$2" task created state newest=0 newest_state=""
  for task in $(docker service ps --quiet --no-trunc "${STACK_NAME}_${MIGRATION_SERVICE}" 2>/dev/null); do
    docker inspect --format '{{range .Spec.ContainerSpec.Env}}{{println .}}{{end}}' "${task}" \
      | grep -qx "CROWDED_RELEASE=${release}" || continue
    read -r created state < <(docker inspect --format '{{.CreatedAt.Unix}} {{.Status.State}}' "${task}")
    if (( created >= since && created >= newest )); then
      newest="${created}"
      newest_state="${state}"
    fi
  done
  printf '%s' "${newest_state}"
}

# Waits until the release's migration job, started by this deployment, has
# completed, and fails as soon as it fails. A release from before the job
# existed has nothing to wait for. Swarm may run the job once more when step 2
# of deploy_release deploys the stack again; that run finds nothing left to
# migrate.
wait_for_migration() {
  local release_dir="$1" since="${DEPLOY_STARTED:?deploy_release sets DEPLOY_STARTED}"
  local release state deadline
  grep -q '^DATABASE_IMAGE=' "${release_dir}/release.env" || return 0
  release="$(basename "${release_dir}")"
  deadline=$(( $(date +%s) + WAIT_TIMEOUT_SECONDS ))

  while true; do
    state="$(migration_state "${release}" "${since}")"
    case "${state}" in
      complete)
        log "Database migrated."
        return 0
        ;;
      failed | rejected | shutdown | orphaned | remove)
        log "The database migration ended in state '${state}'. Its log:"
        docker service logs --raw --tail 60 "${STACK_NAME}_${MIGRATION_SERVICE}" >&2 || true
        return 1
        ;;
    esac
    if (( $(date +%s) >= deadline )); then
      log "Timed out after ${WAIT_TIMEOUT_SECONDS}s waiting for the database migration (${state:-not started})."
      return 1
    fi
    sleep 3
  done
}

# The image digest an application service should run in a release.
expected_digest() {
  local release_dir="$1" service="$2" var
  var="$(image_var "${service}")"
  sed -n "s/^${var}=.*@\(sha256:[0-9a-f]*\).*/\1/p" "${release_dir}/release.env"
}

# Waits until every service runs all its replicas, every application service
# runs the image of the release, and every update this deployment started has
# completed. Updates start the new task first (start-first), so the new image
# and a full replica count show before the new task is healthy; only the
# update state says it was. When an update fails, Swarm rolls the service back
# by itself (update_config.failure_action): the service leaves the release's
# image, and the wait fails at once. A Swarm rollback that lands on the
# release's image (while rolling back to an earlier release) is fine.
# Usage: wait_for_release <release dir>, right after deploy_release.
wait_for_release() {
  local release_dir="$1" since="${DEPLOY_STARTED:?deploy_release sets DEPLOY_STARTED}"
  local deadline pending service replicas running desired state started image expected
  deadline=$(( $(date +%s) + WAIT_TIMEOUT_SECONDS ))

  while true; do
    pending=()
    while read -r service replicas; do
      # The migration job completed before this wait started.
      [[ "${service}" == "${STACK_NAME}_${MIGRATION_SERVICE}" ]] && continue
      # "2/2" or "1/1 (max 1 per node)": running equals desired, and not 0/0.
      running="${replicas%%/*}"
      desired="${replicas#*/}"
      desired="${desired%% *}"
      [[ "${running}" == "${desired}" && "${desired}" != "0" ]] || pending+=("${service} ${replicas}")

      # Application services must run the release's image; the others have
      # no expected digest.
      expected="$(expected_digest "${release_dir}" "${service#"${STACK_NAME}"_}")"
      read -r image state started < <(docker service inspect "${service}" --format \
        '{{.Spec.TaskTemplate.ContainerSpec.Image}} {{if .UpdateStatus}}{{.UpdateStatus.State}} {{if .UpdateStatus.StartedAt}}{{.UpdateStatus.StartedAt.Unix}}{{end}}{{end}}')
      if [[ -n "${expected}" && "${image}" != *"${expected}"* ]]; then
        if [[ "${state:-}" == rollback_* || "${state:-}" == *paused ]]; then
          log "${service}: update ${state}; Swarm could not start ${expected}."
          return 1
        fi
        pending+=("${service} image ${image}")
        continue
      fi

      # The last update, if this deployment started it, must have finished.
      if [[ -n "${state:-}" && "${started:-0}" -ge "${since}" ]]; then
        case "${state}" in
          completed) ;;
          rollback_completed) [[ -n "${expected}" ]] || { log "${service}: update ${state}."; return 1; } ;;
          *paused)
            log "${service}: update ${state}."
            return 1
            ;;
          *) pending+=("${service} update ${state}") ;;
        esac
      fi
    done < <(docker stack services "${STACK_NAME}" --format '{{.Name}} {{.Replicas}}')

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
