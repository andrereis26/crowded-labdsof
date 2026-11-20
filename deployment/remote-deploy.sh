#!/usr/bin/env bash
# Deploys Crowded to the Docker Swarm on this host. Run on the host, from the
# deployment/ folder that CI copies there. Every step can be run again:
#
#   1. Prepares the host: installs Docker Engine and the Doppler CLI if they are
#      missing and initialises a single-node Swarm.
#   2. Resolves the image of every application service to a digest. A service
#      without a new image keeps the one it runs now.
#   3. Copies the secrets from Doppler into Swarm secrets, named after a hash of
#      their value, so a changed secret is a new secret and an unchanged one is
#      reused.
#   4. Records all of it as a release under $DEPLOY_ROOT/releases/<id>/ and
#      deploys that release with `docker stack deploy`: first the database
#      migration job, then, once it completed, the new images of the services.
#   5. Waits until every service is running the release and the router answers.
#      Otherwise it deploys the previous release again and fails.
#
# Usage:  remote-deploy.sh                 deploy (environment below)
#         remote-deploy.sh --bootstrap-only   only prepare the host
#
# Environment:
#   RELEASE_ID            required, the commit SHA being deployed
#   DOCKERHUB_NAMESPACE   required, Docker Hub user or organization of the images
#   DOPPLER_TOKEN         required, Doppler service token of the environment's config
#   DATABASE_IMAGE, CORE_IMAGE, JOBS_IMAGE, NOTIFICATIONS_IMAGE, BACKOFFICE_IMAGE,
#   PARTICIPANT_APP_IMAGE optional, image (tag or digest) for that service;
#                         default: the current release's, else <namespace>/crowded-<service>:main
#   TLS_DOMAIN            the domain of the router's certificate; default: the
#                         current release's
#   STACK_NAME, DEPLOY_ROOT, KEEP_RELEASES, WAIT_TIMEOUT_SECONDS, SMOKE_URL,
#   SWARM_ADVERTISE_ADDR  optional, see lib/common.sh and README.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source-path=SCRIPTDIR source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

# Doppler key -> Swarm secret. The stack file reads the secret's versioned name
# from <KEY>_SECRET.
SECRET_KEYS=(POSTGRES_PASSWORD CORE_DB_PASSWORD JOBS_DB_PASSWORD NOTIFICATIONS_DB_PASSWORD
  RABBITMQ_PASSWORD REDIS_PASSWORD JWT_PRIVATE_KEY JWT_PUBLIC_KEY TICKET_ENTRY_CODE_KEY GROQ_API_KEY
  GRAFANA_ADMIN_PASSWORD)
# Doppler keys that are configuration, not secrets: written to the release as is.
CONFIG_KEYS=(POSTGRES_DB POSTGRES_USER RABBITMQ_USERNAME)
# Configuration that may be missing from Doppler, and its default then.
OPTIONAL_CONFIG_KEYS=(SEED_DEMO_DATA)
# shellcheck disable=SC2034 # read through ${!default}
SEED_DEMO_DATA_DEFAULT=false

# --- 1. Host -----------------------------------------------------------------

apt_install() {
  as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$@" >/dev/null
}

install_docker() {
  log "Installing Docker Engine from Docker's apt repository"
  # shellcheck source=/dev/null
  source /etc/os-release
  [[ "${ID}" == "ubuntu" || "${ID}" == "debian" ]] || die "unsupported OS: ${ID}; install Docker Engine first"
  as_root apt-get update -qq
  apt_install ca-certificates curl
  as_root install -m 0755 -d /etc/apt/keyrings
  as_root curl -fsSL "https://download.docker.com/linux/${ID}/gpg" -o /etc/apt/keyrings/docker.asc
  as_root chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${ID} ${VERSION_CODENAME} stable" \
    | as_root tee /etc/apt/sources.list.d/docker.list >/dev/null
  as_root apt-get update -qq
  apt_install docker-ce docker-ce-cli containerd.io
  as_root systemctl enable --now docker
  # Takes effect at the next login; until then this script uses sudo.
  [[ "${EUID}" -eq 0 ]] || as_root usermod -aG docker "$(id -un)"
}

install_doppler() {
  log "Installing the Doppler CLI from Doppler's apt repository"
  apt_install apt-transport-https ca-certificates curl gnupg
  curl -sLf --retry 3 --tlsv1.2 --proto "=https" \
    'https://packages.doppler.com/public/cli/gpg.DE2A7741A397C129.key' \
    | as_root gpg --dearmor --yes -o /usr/share/keyrings/doppler-archive-keyring.gpg
  echo "deb [signed-by=/usr/share/keyrings/doppler-archive-keyring.gpg] https://packages.doppler.com/public/cli/deb/debian any-version main" \
    | as_root tee /etc/apt/sources.list.d/doppler-cli.list >/dev/null
  as_root apt-get update -qq
  apt_install doppler
}

check_docker_runtime() {
  # Overlay networks do not work with rootless Docker, and Swarm refuses to run
  # with live-restore enabled.
  if docker info --format '{{.SecurityOptions}}' | grep -qi rootless; then
    die "rootless Docker detected; Swarm overlay networks need a rootful Docker Engine"
  fi
  if [[ "$(docker info --format '{{.LiveRestoreEnabled}}')" == "true" ]]; then
    die "live-restore is enabled; set \"live-restore\": false in /etc/docker/daemon.json and restart Docker"
  fi
}

ensure_swarm() {
  local state address
  state="$(docker info --format '{{.Swarm.LocalNodeState}}')"
  [[ "${state}" == "active" ]] && return 0
  [[ "${state}" == "inactive" ]] || die "Swarm is in state '${state}'; fix it by hand (docker swarm leave --force?)"

  # The address of the default route's interface, unless one is given.
  address="${SWARM_ADVERTISE_ADDR:-$(ip -4 route get 1.1.1.1 | awk '{ for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1) }')}"
  log "Initialising a single-node Swarm on ${address}"
  docker swarm init --advertise-addr "${address}" >/dev/null
}

prepare_host() {
  require_command curl
  # type -P, not command -v: lib/common.sh defines a docker function.
  type -P docker >/dev/null 2>&1 || install_docker
  type -P doppler >/dev/null 2>&1 || install_doppler
  detect_docker_access
  check_docker_runtime
  ensure_swarm
}

# --- 2. Images ---------------------------------------------------------------

# Value of KEY in the current release, if there is one.
current_value() {
  local current
  current="$(current_release || true)"
  [[ -n "${current}" ]] || return 0
  sed -n "s/^$1=//p" "${RELEASES_DIR}/${current}/release.env"
}

# Pulls an image and prints it as repository@digest.
pin_image() {
  local image="$1" repository digests
  repository="${image%@*}"
  [[ "${repository##*/}" == *:* ]] && repository="${repository%:*}"
  docker pull --quiet "${image}" >/dev/null || die "cannot pull ${image}"
  digests="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "${image}")"
  # The digest of this repository, if the image is also known under another one.
  grep -m1 "^${repository}@" <<<"${digests}" || head -n1 <<<"${digests}"
}

resolve_images() {
  local service var requested
  for service in "${APP_SERVICES[@]}"; do
    var="$(image_var "${service}")"
    requested="${!var:-$(current_value "${var}")}"
    requested="${requested:-${DOCKERHUB_NAMESPACE}/crowded-${service}:main}"
    printf -v "${var}" '%s' "$(pin_image "${requested}")"
    log "${service}: ${!var}"
  done
}

# --- 3. Secrets ----------------------------------------------------------------

doppler_get() {
  doppler secrets get "$1" --plain --no-check-version
}

# Creates the Swarm secret <stack>_<key>_<hash> from stdin unless it exists,
# and prints its name.
create_secret() {
  local key="$1" value hash name
  value="$(cat)"
  hash="$(printf '%s' "${value}" | sha256sum | cut -c1-12)"
  name="${STACK_NAME}_${key,,}_${hash}"
  if ! docker secret inspect "${name}" >/dev/null 2>&1; then
    printf '%s' "${value}" | docker secret create --label "crowded.stack=${STACK_NAME}" "${name}" - >/dev/null
    log "Created secret ${name}"
  fi
  printf '%s' "${name}"
}

create_secrets() {
  local key value rabbitmq_password=""
  for key in "${SECRET_KEYS[@]}"; do
    value="$(doppler_get "${key}")" || die "Doppler has no ${key}"
    [[ -n "${value}" ]] || die "Doppler's ${key} is empty"
    [[ "${key}" == RABBITMQ_PASSWORD ]] && rabbitmq_password="${value}"
    printf -v "${key}_SECRET" '%s' "$(printf '%s' "${value}" | create_secret "${key}")"
  done

  # RabbitMQ reads its default user from a config file, not from a secret file.
  # Read by write_release through ${!var}.
  # shellcheck disable=SC2034
  printf -v RABBITMQ_CONF_SECRET '%s' "$(printf 'default_user = %s\ndefault_pass = %s\n' \
    "${RABBITMQ_USERNAME}" "${rabbitmq_password}" | create_secret RABBITMQ_CONF)"
}

# --- 4. Release ------------------------------------------------------------------

write_release() {
  local release_dir="$1" key var service
  mkdir -p "${release_dir}"
  cp "${SCRIPT_DIR}/${STACK_FILE_NAME}" "${release_dir}/"
  cp -r "${SCRIPT_DIR}/router" "${SCRIPT_DIR}/observability" "${release_dir}/"

  {
    printf '# Release %s, deployed %s\n' "${RELEASE_ID}" "$(date -u +%FT%TZ)"
    printf '%s=%q\n' RELEASE_ID "${RELEASE_ID}"
    for service in "${APP_SERVICES[@]}"; do
      var="$(image_var "${service}")"
      printf '%s=%q\n' "${var}" "${!var}"
    done
    for key in "${CONFIG_KEYS[@]}" "${OPTIONAL_CONFIG_KEYS[@]}"; do
      printf '%s=%q\n' "${key}" "${!key}"
    done
    for key in "${SECRET_KEYS[@]}" RABBITMQ_CONF; do
      var="${key}_SECRET"
      printf '%s=%q\n' "${var}" "${!var}"
    done
    # Swarm configs cannot change either, so the router's is versioned the same way.
    printf '%s=%q\n' ROUTER_CONF_VERSION "$(cat "${release_dir}/router/nginx.conf" "${release_dir}/router/routes.conf" | sha256sum | cut -c1-12)"
    printf '%s=%q\n' TLS_DOMAIN "${TLS_DOMAIN}"
    printf '%s=%q\n' OBSERVABILITY_CONF_VERSION "$(observability_version "${release_dir}/observability")"
  } >"${release_dir}/release.env"
  chmod 600 "${release_dir}/release.env"
}

# --- 5. Verify, roll back, clean up ------------------------------------------------

roll_back_to() {
  local previous="$1"
  if [[ -z "${previous}" ]]; then
    log "No previous release to roll back to."
    return 1
  fi
  log "Rolling back to release ${previous}"
  deploy_release "${RELEASES_DIR}/${previous}" && wait_for_release "${RELEASES_DIR}/${previous}" && smoke_test
}

# Keeps the newest $KEEP_RELEASES releases and the secrets they use.
clean_up() {
  local release secret
  mapfile -t releases < <(list_releases)
  if (( ${#releases[@]} > KEEP_RELEASES )); then
    for release in "${releases[@]:0:${#releases[@]}-KEEP_RELEASES}"; do
      rm -rf -- "${RELEASES_DIR:?}/${release}"
    done
  fi
  find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -name '*.failed' -mtime +14 -exec rm -rf -- {} +

  local in_use
  # Secrets in use by a running service cannot be removed; docker refuses.
  in_use="$(cat "${RELEASES_DIR}"/*/release.env 2>/dev/null)"
  while read -r secret; do
    grep -q "=${secret}\$" <<<"${in_use}" || docker secret rm "${secret}" >/dev/null 2>&1 || true
  done < <(docker secret ls --filter "label=crowded.stack=${STACK_NAME}" --format '{{.Name}}')

  # Configs are named crowded_<name>_<version>, the version being
  # ROUTER_CONF_VERSION or OBSERVABILITY_CONF_VERSION.
  local config
  while read -r config; do
    grep -q "=${config##*_}\$" <<<"${in_use}" || docker config rm "${config}" >/dev/null 2>&1 || true
  done < <(docker config ls --filter "label=com.docker.stack.namespace=${STACK_NAME}" --format '{{.Name}}')

  docker image prune --all --force --filter "until=336h" >/dev/null
}

main() {
  prepare_host
  if [[ "${1:-}" == "--bootstrap-only" ]]; then
    log "Host ready."
    return 0
  fi

  : "${RELEASE_ID:?RELEASE_ID is required}"
  : "${DOCKERHUB_NAMESPACE:?DOCKERHUB_NAMESPACE is required}"
  : "${DOPPLER_TOKEN:?DOPPLER_TOKEN is required}"
  export DOPPLER_TOKEN
  [[ "${RELEASE_ID}" =~ ^[0-9A-Za-z._-]+$ ]] || die "RELEASE_ID may only contain letters, digits, '.', '_' and '-'"

  local key previous release release_dir
  for key in "${CONFIG_KEYS[@]}"; do
    printf -v "${key}" '%s' "$(doppler_get "${key}")" || die "Doppler has no ${key}"
    [[ -n "${!key}" ]] || die "Doppler's ${key} is empty"
  done
  local default
  for key in "${OPTIONAL_CONFIG_KEYS[@]}"; do
    default="${key}_DEFAULT"
    printf -v "${key}" '%s' "$(doppler_get "${key}" 2>/dev/null || true)"
    printf -v "${key}" '%s' "${!key:-${!default}}"
  done
  [[ "${SEED_DEMO_DATA}" == true || "${SEED_DEMO_DATA}" == false ]] \
    || die "Doppler's SEED_DEMO_DATA must be true or false"

  previous="$(current_release || true)"
  release="$(date -u +%Y%m%dT%H%M%SZ)-${RELEASE_ID:0:12}"
  release_dir="${RELEASES_DIR}/${release}"

  TLS_DOMAIN="${TLS_DOMAIN:-$(current_value TLS_DOMAIN)}"
  [[ -n "${TLS_DOMAIN}" ]] || die "TLS_DOMAIN is required: the domain of the router's certificate"
  resolve_images
  create_secrets
  write_release "${release_dir}"
  log "Deploying release ${release}"

  if deploy_release "${release_dir}" && wait_for_release "${release_dir}" && smoke_test; then
    mark_current "${release}"
    clean_up
    log "Release ${release} is live."
    return 0
  fi

  print_service_states
  mv "${release_dir}" "${release_dir}.failed"
  if roll_back_to "${previous}"; then
    log "Release ${release} failed; ${previous} is live again."
  else
    log "Release ${release} failed and the rollback did not finish. Check the services by hand."
  fi
  return 1
}

main "$@"
