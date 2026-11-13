#!/usr/bin/env bash
# Runs one app from source on this machine, in the background, against the
# infrastructure of infra-up.sh (or the data stores of stack-up.sh), with its
# configuration from .env:
#   core, jobs, notifications  ./mvnw spring-boot:run, as the service's own
#                              database user, on CORE_PORT, JOBS_PORT...
#   backoffice                 npm run dev, on BACKOFFICE_PORT
#   participant-app            flutter run -d web-server, on PARTICIPANT_APP_PORT
# It waits until the app listens on its port. The app's output goes to
# .local/logs/<app>.log; stop it with stop.sh. Running it again for an app
# that runs does nothing.
# Usage: scripts/local/start.sh <core|jobs|notifications|backoffice|participant-app>
# Windows twin: scripts/local/start.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

usage="Usage: scripts/local/start.sh <$(printf '%s' "${APPS}" | tr ' ' '|')>"
app="${1:-}"
case "${app}" in -h | --help)
  echo "${usage}"
  exit 0
  ;;
esac
is_app "${app}" && [[ $# -eq 1 ]] || die "${usage}"
# A first Maven or Flutter build downloads its dependencies.
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"

load_env
if pid="$(app_pid "${app}")"; then
  log "${app} already runs (process ${pid})."
  exit 0
fi
port="$(app_port "${app}")"
port_open "${port}" && die "port ${port} (${app}) is already in use."

dir="${ROOT}/${app}"
env_vars=()
case "${app}" in
  core | jobs | notifications)
    [[ "$(java_major)" == 25 ]] || die "Maven needs JDK 25: set JAVA_HOME to a JDK 25 installation."
    port_open "${DB_PORT}" || die "nothing listens on 127.0.0.1:${DB_PORT}: run scripts/local/infra-up.sh first."
    write_jwt_keys
    password_var="$(upper_name "${app}")_DB_PASSWORD"
    env_vars=(
      "SERVER_PORT=${port}"
      "DB_URL=jdbc:postgresql://127.0.0.1:${DB_PORT}/${POSTGRES_DB}"
      "DB_USERNAME=crowded_${app}"
      "DB_PASSWORD=${!password_var}"
      "RABBITMQ_HOST=127.0.0.1"
      "RABBITMQ_PORT=${RABBITMQ_PORT}"
      "RABBITMQ_USERNAME=${RABBITMQ_USERNAME}"
      "RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}"
      "REDIS_HOST=127.0.0.1"
      "REDIS_PORT=${REDIS_PORT}"
      "REDIS_PASSWORD=${REDIS_PASSWORD}"
      "JWT_PUBLIC_KEY_LOCATION=file:${STATE_DIR}/jwt/public.pem"
    )
    # Only Core signs tokens.
    [[ "${app}" == core ]] && env_vars+=("JWT_PRIVATE_KEY_LOCATION=file:${STATE_DIR}/jwt/private.pem")
    command=(./mvnw -B -ntp spring-boot:run)
    ;;
  backoffice)
    command -v npm >/dev/null 2>&1 || die "npm is missing: run scripts/local/setup.sh."
    if [[ ! -d "${dir}/node_modules" ]]; then
      log "Installing the backoffice's dependencies"
      npm --prefix "${dir}" ci --no-audit --no-fund
    fi
    command=(npm run dev -- --host 127.0.0.1 --port "${port}" --strictPort)
    ;;
  participant-app)
    command -v flutter >/dev/null 2>&1 || die "flutter is missing: run scripts/local/setup.sh."
    command=(flutter run -d web-server --web-hostname 127.0.0.1 --web-port "${port}")
    ;;
esac

log_path="$(log_file "${app}")"
mkdir -p "$(dirname "${log_path}")" "$(dirname "$(pid_file "${app}")")"
log "Starting ${app} (log: ${log_path#"${ROOT}"/})"
# Job control gives the app its own process group, so that stop.sh stops it
# with everything it started (Maven's forked JVM, Vite, Flutter's tools).
# Each app gets its own settings and none of the other values of .env.
set -m
(
  cd "${dir}"
  # shellcheck disable=SC2046 # one name per word
  unset $(env_keys)
  [[ ${#env_vars[@]} -eq 0 ]] || export "${env_vars[@]}"
  exec nohup "${command[@]}" >"${log_path}" 2>&1 </dev/null
) &
pid=$!
set +m
printf '%s\n' "${pid}" >"$(pid_file "${app}")"

deadline=$(($(date +%s) + TIMEOUT_SECONDS))
until port_open "${port}"; do
  if ! kill -0 "${pid}" 2>/dev/null; then
    rm -f "$(pid_file "${app}")"
    tail -n 40 "${log_path}" >&2
    die "${app} stopped while starting. Its log: ${log_path}"
  fi
  if (($(date +%s) >= deadline)); then
    "$(dirname "$0")/stop.sh" "${app}"
    die "${app} did not listen on port ${port} within ${TIMEOUT_SECONDS}s. Its log: ${log_path}"
  fi
  sleep 2
done
log "${app} runs on http://localhost:${port}/ (process ${pid}). Stop it with scripts/local/stop.sh ${app}."
