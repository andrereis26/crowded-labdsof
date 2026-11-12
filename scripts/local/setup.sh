#!/usr/bin/env bash
# Prepares a fresh clone for local development, on Debian or macOS:
#   1. checks the tools and versions the projects need, and says how to
#      install any that is missing;
#   2. creates .env from .env.example, with a freshly generated random value
#      for every secret, unless .env exists: it is never overwritten;
#   3. installs the Git hooks (lefthook install).
# Exits with an error while a tool is missing; run it again once installed.
# Usage: scripts/local/setup.sh
# Windows twin: scripts/local/setup.ps1

set -euo pipefail
# shellcheck source=scripts/local/lib.sh
source "$(dirname "$0")/lib.sh"

case "${1:-}" in
  "") ;;
  -h | --help)
    echo "Usage: scripts/local/setup.sh"
    exit 0
    ;;
  *) die "unknown option: $1 (setup.sh takes none)" ;;
esac

if [[ "$(uname -s)" == Darwin ]]; then os=macos; else os=debian; fi
missing=0

ok() { printf '  ok       %s\n' "$1"; }
# need <what> <Debian hint> <macOS hint>
need() {
  local hint="$2"
  [[ "${os}" == macos ]] && hint="$3"
  printf '  missing  %s\n           %s\n' "$1" "${hint}"
  missing=$((missing + 1))
}
# The first x.y.z (or x.y) in stdin.
version_of() { grep -Eo '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -n 1; }
# version_at_least <version> <major> <minor>
version_at_least() {
  local major="${1%%.*}" minor="${1#*.}"
  minor="${minor%%.*}"
  ((major > $2 || (major == $2 && minor >= $3)))
}

check_tools() {
  local version docker_running=false
  log "Checking the tools"

  if ! command -v docker >/dev/null 2>&1; then
    need "Docker with Compose v2" "https://docs.docker.com/engine/install/debian/" "brew install --cask docker (Docker Desktop)"
  elif ! docker info >/dev/null 2>&1; then
    need "Docker is installed but not running" "sudo systemctl start docker, and add yourself to the docker group" "open Docker Desktop"
  elif ! version="$(docker compose version --short 2>/dev/null)"; then
    need "Docker Compose v2 (docker compose)" "sudo apt install docker-compose-plugin" "update Docker Desktop"
  else
    docker_running=true
    ok "Docker $(docker version --format '{{.Server.Version}}'), Compose ${version}"
  fi

  version="$(java_major)"
  if [[ "${version}" == 25 ]]; then
    ok "JDK 25 (${JAVA_HOME:-java on PATH})"
  else
    need "JDK 25 as JAVA_HOME (found: ${version:-none})" \
      "install temurin-25-jdk (https://adoptium.net/installation/linux/) and export JAVA_HOME" \
      "brew install --cask temurin@25, then export JAVA_HOME=\$(/usr/libexec/java_home -v 25)"
  fi

  version="$(node --version 2>/dev/null | version_of || true)"
  if [[ -n "${version}" ]] && version_at_least "${version}" 22 12; then
    ok "Node.js ${version}"
  else
    need "Node.js 22.12 or later; CI uses 24 (found: ${version:-none})" \
      "https://nodejs.org/en/download (nvm or the NodeSource packages)" "brew install node@24"
  fi

  version="$(flutter --version 2>/dev/null | sed -n 's/^Flutter \([0-9.]*\).*/\1/p' | head -n 1 || true)"
  if [[ "${version}" == 3.47.* ]]; then
    ok "Flutter ${version}"
  else
    need "Flutter 3.47, the CI version 3.47.7 (found: ${version:-none})" \
      "https://docs.flutter.dev/install/archive (3.47.7)" "https://docs.flutter.dev/install/archive (3.47.7)"
  fi

  version="$(lefthook version 2>/dev/null | version_of || true)"
  if [[ "${version}" == 2.* ]]; then
    ok "Lefthook ${version}"
  else
    need "Lefthook 2 (found: ${version:-none})" "npm install -g lefthook" "brew install lefthook"
  fi

  version="$(gitleaks version 2>/dev/null | version_of || true)"
  if [[ "${version}" == 8.30.1 ]]; then
    ok "gitleaks ${version}"
  elif [[ "${docker_running}" == true ]]; then
    ok "gitleaks 8.30.1 through its Docker image (scripts/gitleaks.sh)"
  else
    need "gitleaks 8.30.1, or Docker running" \
      "https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1" "https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1"
  fi

  if command -v openssl >/dev/null 2>&1; then
    ok "OpenSSL (JWT keys)"
  else
    need "OpenSSL, for the JWT keys" "sudo apt install openssl" "it comes with macOS: check your PATH"
  fi
}

# Writes .env: .env.example with every empty value filled with a new secret.
create_env() {
  local line key value private public
  command -v openssl >/dev/null 2>&1 || {
    warn "Not creating .env without OpenSSL."
    return 0
  }
  tmp="$(mktemp -d)"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${tmp}/private.pem" 2>/dev/null
  openssl rsa -in "${tmp}/private.pem" -pubout -out "${tmp}/public.pem" 2>/dev/null
  private="$(openssl base64 -A -in "${tmp}/private.pem")"
  public="$(openssl base64 -A -in "${tmp}/public.pem")"

  (
    umask 077
    while IFS= read -r line || [[ -n "${line}" ]]; do
      if [[ "${line}" =~ ^([A-Za-z_][A-Za-z0-9_]*)=$ ]]; then
        key="${BASH_REMATCH[1]}"
        case "${key}" in
          JWT_PRIVATE_KEY_BASE64) value="${private}" ;;
          JWT_PUBLIC_KEY_BASE64) value="${public}" ;;
          # Hex: letters and digits only, as the database users and RabbitMQ need.
          *) value="$(openssl rand -hex 24)" ;;
        esac
        line="${key}=${value}"
      fi
      printf '%s\n' "${line}"
    done <"${ENV_EXAMPLE}" >"${tmp}/env"
  )
  mv "${tmp}/env" "${ENV_FILE}"
  log "Created .env with new random secrets."
}

tmp=""
trap 'rm -rf -- "${tmp}"' EXIT

check_tools

if [[ -f "${ENV_FILE}" ]]; then
  log "Keeping the existing .env."
  absent=""
  for key in $(env_keys); do
    grep -q "^${key}=" "${ENV_FILE}" || absent="${absent} ${key}"
  done
  [[ -z "${absent}" ]] || warn ".env lacks${absent}: copy them from .env.example."
else
  create_env
fi

if command -v lefthook >/dev/null 2>&1; then
  log "Installing the Git hooks"
  (cd "${ROOT}" && lefthook install)
fi

if ((missing > 0)); then
  die "${missing} tool(s) missing or at another version: install them and run this again."
fi
log "Ready. Next: scripts/local/stack-up.sh for the whole system, or scripts/local/infra-up.sh and scripts/local/start.sh <app>."
