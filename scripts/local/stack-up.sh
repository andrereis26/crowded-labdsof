#!/usr/bin/env bash
# Runs the whole system on this machine as the production environment runs it:
# the Swarm stack of deployment/docker-compose.swarm.yml, with the
# local override stack.local.yml. It
#   1. turns on single-node Swarm if it is off, on 127.0.0.1;
#   2. builds every app's image from the working copy (the participant app
#      runs flutter build web first), or with --pull pulls the Docker Hub
#      images tagged main;
#   3. creates the Swarm secrets from .env, named after a hash of their value;
#   4. deploys the stack and its overlay networks: the database job migrates
#      and seeds the database before Core, Jobs and Notifications first start;
#   5. starts the gateway, which publishes the system and the data stores on
#      127.0.0.1 only (Swarm cannot bind a port to one address);
#   6. waits until every service is healthy and prints the URLs.
# Running it again updates what changed. stack-down.sh removes it all.
# Usage: scripts/local/stack-up.sh [--pull] [--with-observability]
#   --pull                 use the images tagged main instead of building them
#   --with-observability   add Prometheus, Loki, Alloy and Grafana
# Windows twin: scripts/local/stack-up.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

usage="Usage: scripts/local/stack-up.sh [--pull] [--with-observability]"
pull=false
observability=false
for arg in "$@"; do
  case "${arg}" in
    --pull) pull=true ;;
    --with-observability) observability=true ;;
    -h | --help)
      echo "${usage}"
      exit 0
      ;;
    *) die "unknown option: ${arg}. ${usage}" ;;
  esac
done
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"

stack_files=(-c "${STACK_FILE}" -c "${LOCAL_DIR}/stack.local.yml")
if [[ "${observability}" == true ]]; then
  [[ -f "${OBSERVABILITY_FILE}" ]] \
    || die "--with-observability needs ${OBSERVABILITY_FILE#"${ROOT}"/}, which this checkout does not have yet."
  stack_files+=(-c "${OBSERVABILITY_FILE}")
fi

load_env
require_docker
if infra_running; then
  die "the infrastructure of infra-up.sh uses the same ports: run scripts/local/infra-down.sh first."
fi

# --- 1. Swarm -------------------------------------------------------------------
case "$(docker info --format '{{.Swarm.LocalNodeState}}')" in
  active) ;;
  inactive)
    log "Turning on single-node Swarm"
    docker swarm init --advertise-addr 127.0.0.1 --listen-addr 127.0.0.1:2377 >/dev/null
    ;;
  *) die "Swarm is in state '$(docker info --format '{{.Swarm.LocalNodeState}}')': fix it, or turn it off with docker swarm leave --force." ;;
esac

# --- 2. Images ------------------------------------------------------------------
if [[ "${pull}" == true ]]; then
  for service in ${IMAGE_SERVICES}; do
    image="${DOCKERHUB_NAMESPACE}/crowded-${service}:main"
    log "Pulling ${image}"
    docker pull --quiet "${image}" >/dev/null
    export "$(upper_name "${service}")_IMAGE=${image}"
  done
else
  command -v flutter >/dev/null 2>&1 || die "flutter is missing: install it (setup.sh), or use --pull."
  log "Building the participant app for the web"
  (cd "${ROOT}/participant-app" && flutter build web --release)
  for service in ${IMAGE_SERVICES}; do
    image="crowded/${service}:local"
    log "Building ${image}"
    docker build --quiet --tag "${image}" "${ROOT}/${service}" >/dev/null
    # Deployed by a tag made from the image id: Swarm keeps a service running
    # while its image reference is unchanged, so a rebuilt image needs a new tag,
    # and an unchanged one keeps its tag and its containers.
    id="$(docker image inspect --format '{{.Id}}' "${image}")"
    image="crowded/${service}:local-$(printf '%s' "${id#sha256:}" | cut -c1-12)"
    docker tag "crowded/${service}:local" "${image}"
    export "$(upper_name "${service}")_IMAGE=${image}"
  done
fi

# --- 3. Secrets -----------------------------------------------------------------
# create_secret <KEY> <value>: creates the secret crowded_local_<key>_<hash>
# unless it exists, and exports its name as <KEY>_SECRET, which the stack file
# reads. Not in a pipeline: the export would be lost in its subshell.
secret_names=" "
create_secret() {
  local key="$1" value="$2" name
  name="${STACK_NAME}_local_$(printf '%s' "${key}" | tr '[:upper:]' '[:lower:]')_$(printf '%s' "${value}" | sha256_12)"
  if ! docker secret inspect "${name}" >/dev/null 2>&1; then
    printf '%s' "${value}" | docker secret create --label "${LOCAL_LABEL}" "${name}" - >/dev/null
  fi
  export "${key}_SECRET=${name}"
  secret_names="${secret_names}${name} "
}

log "Creating the secrets"
for key in POSTGRES_PASSWORD CORE_DB_PASSWORD JOBS_DB_PASSWORD NOTIFICATIONS_DB_PASSWORD RABBITMQ_PASSWORD REDIS_PASSWORD; do
  create_secret "${key}" "${!key}"
done
create_secret JWT_PRIVATE_KEY "$(printf '%s' "${JWT_PRIVATE_KEY_BASE64}" | openssl base64 -d -A)"
create_secret JWT_PUBLIC_KEY "$(printf '%s' "${JWT_PUBLIC_KEY_BASE64}" | openssl base64 -d -A)"
# RabbitMQ reads its default user from a config file, not from a secret file.
create_secret RABBITMQ_CONF "$(printf 'default_user = %s\ndefault_pass = %s' "${RABBITMQ_USERNAME}" "${RABBITMQ_PASSWORD}")"

# --- 4. Deployment --------------------------------------------------------------
# Swarm configs cannot change, so the router's is named after its hash.
ROUTER_CONF_VERSION="$(sha256_12 <"${ROOT}/deployment/router/nginx.conf")"
# The migration job runs again whenever the database image changes.
CROWDED_RELEASE="local-$(docker image inspect --format '{{.Id}}' "${DATABASE_IMAGE}" | sha256_12)"
export ROUTER_CONF_VERSION CROWDED_RELEASE

deploy() {
  docker stack deploy --prune --resolve-image never "${stack_files[@]}" "${STACK_NAME}" >/dev/null
}

if ! docker service inspect "${STACK_NAME}_core" >/dev/null 2>&1; then
  # First deployment: Core, Jobs and Notifications wait for the migration.
  log "Deploying the stack and migrating the database"
  (
    export CORE_REPLICAS=0 JOBS_REPLICAS=0 NOTIFICATIONS_REPLICAS=0
    deploy
  )
  wait_for_job "${STACK_NAME}_database" "${TIMEOUT_SECONDS}"
fi
log "Deploying the stack"
deploy
wait_for_job "${STACK_NAME}_database" "${TIMEOUT_SECONDS}"

# Secrets from an earlier .env, unless a service still uses them.
for secret in $(docker secret ls --filter "label=${LOCAL_LABEL}" --format '{{.Name}}'); do
  case "${secret_names}" in
    *" ${secret} "*) ;;
    *) docker secret rm "${secret}" >/dev/null 2>&1 || true ;;
  esac
done

# --- 5. Gateway -----------------------------------------------------------------
log "Starting the gateway on 127.0.0.1"
docker rm --force "${GATEWAY}" >/dev/null 2>&1 || true
docker run --detach --name "${GATEWAY}" --label "${LOCAL_LABEL}" --restart unless-stopped \
  --network "${STACK_NAME}_edge" \
  --publish "127.0.0.1:${HTTP_PORT}:8080" \
  --publish "127.0.0.1:${DB_PORT}:5432" \
  --publish "127.0.0.1:${RABBITMQ_PORT}:5672" \
  --publish "127.0.0.1:${REDIS_PORT}:6379" \
  --volume "${LOCAL_DIR}/gateway.nginx.conf:/etc/nginx/nginx.conf:ro" \
  --volume "${ROOT}/deployment/router/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  "${GATEWAY_IMAGE}" >/dev/null
docker network connect "${STACK_NAME}_backend" "${GATEWAY}"

# --- 6. Health ------------------------------------------------------------------
log "Waiting until every service is healthy"
deadline=$(($(date +%s) + TIMEOUT_SECONDS))
while true; do
  pending=""
  while read -r name replicas; do
    # The migration job: "0/1 (1/1 completed)". It completed before this wait.
    [[ "${replicas}" == *completed* ]] && continue
    running="${replicas%%/*}"
    desired="${replicas#*/}"
    desired="${desired%% *}"
    [[ "${running}" == "${desired}" ]] || pending="${pending} ${name#"${STACK_NAME}"_}(${replicas})"
    # A service moving to a new image still counts its old task as running.
    update="$(docker service inspect --format '{{if .UpdateStatus}}{{.UpdateStatus.State}}{{end}}' "${name}")"
    [[ "${update}" == updating ]] && pending="${pending} ${name#"${STACK_NAME}"_}(updating)"
  done < <(docker stack services "${STACK_NAME}" --format '{{.Name}} {{.Replicas}}')
  [[ -z "${pending}" ]] && break
  if (($(date +%s) >= deadline)); then
    die "not healthy after ${TIMEOUT_SECONDS}s:${pending}. See scripts/local/status.sh and logs.sh <service>."
  fi
  sleep 5
done

# Tags of earlier builds, now unused. Swarm keeps the stopped containers of
# earlier tasks, which still use theirs.
if [[ "${pull}" != true ]]; then
  for service in ${IMAGE_SERVICES}; do
    current="$(printenv "$(upper_name "${service}")_IMAGE")"
    for tag in $(docker image ls "crowded/${service}" --format '{{.Tag}}'); do
      old="crowded/${service}:${tag}"
      case "${tag}" in
        local-*)
          if [[ "${old}" != "${current}" && -z "$(docker ps --all --quiet --filter "ancestor=${old}")" ]]; then
            docker image rm "${old}" >/dev/null 2>&1 || true
          fi
          ;;
      esac
    done
  done
fi
cat <<EOF

Crowded is running, on 127.0.0.1 only:
  Participant app  http://localhost:${HTTP_PORT}/
  Backoffice       http://localhost:${HTTP_PORT}/backoffice/
  Core API         http://localhost:${HTTP_PORT}/api/
  Notifications    http://localhost:${HTTP_PORT}/api/notifications/
  PostgreSQL       127.0.0.1:${DB_PORT}, database ${POSTGRES_DB} (users and passwords in .env)
  RabbitMQ         127.0.0.1:${RABBITMQ_PORT}
  Redis            127.0.0.1:${REDIS_PORT}
See scripts/local/status.sh and logs.sh <service>; remove it all with stack-down.sh.
EOF
