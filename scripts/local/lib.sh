# Settings and functions shared by the scripts in scripts/local/. Sourced, not
# run. lib.ps1 is its PowerShell twin: keep them in step.
#
# These scripts run on Debian and on macOS, whose bash is 3.2 with BSD tools:
# no associative arrays, ${var^^}, mapfile, or GNU-only options (sed -i,
# find -printf, date -d, readlink -f...).
# shellcheck shell=bash
# shellcheck disable=SC2034 # the settings are used by the scripts that source this file

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_DIR="${ROOT}/scripts/local"
ENV_FILE="${ROOT}/.env"
ENV_EXAMPLE="${ROOT}/.env.example"
# Git-ignored files the scripts write: JWT keys, logs and process ids.
STATE_DIR="${ROOT}/.local"

STACK_NAME=crowded
STACK_FILE="${ROOT}/deployment/docker-compose.swarm.yml"
OBSERVABILITY_FILE="${ROOT}/deployment/observability/docker-compose.observability.yml"
INFRA_PROJECT=crowded-infra
GATEWAY=crowded-local-gateway
# The router's image (deployment/docker-compose.swarm.yml).
GATEWAY_IMAGE=nginxinc/nginx-unprivileged:1.31.7-alpine
# Label of the Swarm secrets and the gateway, which stack-down removes.
LOCAL_LABEL=crowded.local=true

# The apps start.sh runs from source, and the images stack-up.sh builds
# (database is the migration job, ADR 0012).
APPS="core jobs notifications backoffice participant-app"
IMAGE_SERVICES="database core jobs notifications backoffice participant-app"

log() { printf '==> %s\n' "$*" >&2; }
warn() { printf 'Warning: %s\n' "$*" >&2; }
die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

# Keys of .env.example, one per line.
env_keys() {
  sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' "${ENV_EXAMPLE}"
}

# Exports every KEY=value of .env, and fails when a key of .env.example has
# no value. Values are never printed.
load_env() {
  [[ -f "${ENV_FILE}" ]] || die "no .env: run scripts/local/setup.sh first."
  local line key number=0 missing=""
  while IFS= read -r line || [[ -n "${line}" ]]; do
    number=$((number + 1))
    line="${line%$'\r'}"
    case "${line}" in '' | '#'*) continue ;; esac
    key="${line%%=*}"
    if [[ "${line}" != *=* || ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      die ".env, line ${number}: expected KEY=value."
    fi
    export "${key}=${line#*=}"
  done <"${ENV_FILE}"
  for key in $(env_keys); do
    [[ -n "${!key:-}" ]] || missing="${missing} ${key}"
  done
  [[ -z "${missing}" ]] || die ".env has no value for:${missing}. Copy them from .env.example."
}

require_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker is not installed (scripts/local/setup.sh says how)."
  docker info >/dev/null 2>&1 || die "Docker is not running: start it first."
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 (docker compose) is missing."
}

# First 12 hex digits of the SHA-256 of stdin.
sha256_12() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum; else shasum -a 256; fi | cut -c1-12
}

# core -> CORE, participant-app -> PARTICIPANT_APP
upper_name() { printf '%s' "$1" | tr 'a-z-' 'A-Z_'; }

# Writes the JWT key pair of .env to .local/jwt/, readable by the user only.
write_jwt_keys() {
  local dir="${STATE_DIR}/jwt"
  mkdir -p "${dir}"
  chmod 700 "${dir}"
  (
    umask 077
    printf '%s' "${JWT_PRIVATE_KEY_BASE64}" | openssl base64 -d -A >"${dir}/private.pem"
    printf '%s' "${JWT_PUBLIC_KEY_BASE64}" | openssl base64 -d -A >"${dir}/public.pem"
  ) || die "JWT_PRIVATE_KEY_BASE64 or JWT_PUBLIC_KEY_BASE64 in .env is not valid base64."
}

# The major version of the Java that the Maven wrapper runs on: JAVA_HOME's,
# else the one on PATH. Empty when there is none.
java_major() {
  local java="java"
  [[ -n "${JAVA_HOME:-}" ]] && java="${JAVA_HOME}/bin/java"
  { "${java}" -version 2>&1 || true; } | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1
}

swarm_active() {
  [[ "$(docker info --format '{{.Swarm.LocalNodeState}}' 2>/dev/null)" == active ]]
}

stack_exists() {
  swarm_active && docker stack ls --format '{{.Name}}' | grep -qx "${STACK_NAME}"
}

# docker compose for the infrastructure of infra-up.sh.
infra_compose() {
  docker compose --project-name "${INFRA_PROJECT}" \
    -f "${ROOT}/database/compose.yaml" -f "${LOCAL_DIR}/infra.compose.yaml" "$@"
}

infra_running() {
  [[ -n "$(infra_compose ps --quiet 2>/dev/null)" ]]
}

# Whether something accepts connections on 127.0.0.1:<port>.
port_open() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

# Waits until the newest run of the Swarm job <service> has completed, and
# fails, showing its log, as soon as that run fails.
wait_for_job() {
  local service="$1" timeout="${2:-300}" deadline index task states
  deadline=$(($(date +%s) + timeout))
  while true; do
    index="$(docker service inspect --format '{{if .JobStatus}}{{.JobStatus.JobIteration.Index}}{{end}}' "${service}")"
    states=" "
    for task in $(docker service ps --quiet --no-trunc "${service}"); do
      states="${states}$(docker inspect --format '{{if .JobIteration}}{{.JobIteration.Index}}{{end}}={{.Status.State}}' "${task}") "
    done
    case "${states}" in
      *" ${index}=complete "*) return 0 ;;
      *" ${index}=failed "* | *" ${index}=rejected "*)
        log "${service} failed. Its log:"
        docker service logs --raw --tail 60 "${service}" >&2 || true
        return 1
        ;;
    esac
    if (($(date +%s) >= deadline)); then
      log "Timed out after ${timeout}s waiting for ${service}."
      return 1
    fi
    sleep 3
  done
}

# Whether <name> is an app that start.sh runs.
is_app() {
  case " ${APPS} " in *" $1 "*) [[ -n "$1" ]] ;; *) return 1 ;; esac
}

# The port of an app run from source, from .env.
app_port() {
  local var
  var="$(upper_name "$1")_PORT"
  printf '%s' "${!var}"
}

pid_file() { printf '%s/run/%s.pid' "${STATE_DIR}" "$1"; }
log_file() { printf '%s/logs/%s.log' "${STATE_DIR}" "$1"; }

# Prints the process id of an app that start.sh started and that still runs.
app_pid() {
  local file pid
  file="$(pid_file "$1")"
  [[ -f "${file}" ]] || return 1
  pid="$(cat "${file}")"
  [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null && printf '%s' "${pid}"
}
