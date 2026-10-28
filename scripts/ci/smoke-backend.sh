#!/usr/bin/env bash
# Smoke test of a backend image: starts it the way the Swarm stack does
# (deployment/docker-compose.swarm.yml), next to PostgreSQL, RabbitMQ and Redis,
# with the secrets as files in /run/secrets, and waits for its health check.
# Usage: scripts/ci/smoke-backend.sh <image>

set -euo pipefail

IMAGE="${1:?usage: smoke-backend.sh <image>}"
RUN_ID="smoke-$$"
NETWORK="${RUN_ID}"
SECRETS="$(mktemp -d)"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-180}"

# shellcheck disable=SC2329 # called by the trap below
cleanup() {
  docker rm --force --volumes "${RUN_ID}-app" "${RUN_ID}-postgres" "${RUN_ID}-rabbitmq" "${RUN_ID}-redis" >/dev/null 2>&1 || true
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
  rm -rf -- "${SECRETS}"
}
trap cleanup EXIT

password() { openssl rand -hex 16; }

# Same names as the secret targets in the stack file.
password >"${SECRETS}/DB_PASSWORD"
password >"${SECRETS}/RABBITMQ_PASSWORD"
password >"${SECRETS}/REDIS_PASSWORD"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${SECRETS}/jwt_private_key" 2>/dev/null
openssl rsa -in "${SECRETS}/jwt_private_key" -pubout -out "${SECRETS}/jwt_public_key" 2>/dev/null
# The container user is not the runner's user.
chmod 0755 "${SECRETS}"
chmod 0444 "${SECRETS}"/*

docker network create "${NETWORK}" >/dev/null
docker run --detach --name "${RUN_ID}-postgres" --network "${NETWORK}" --network-alias postgres \
  -e POSTGRES_DB=crowded -e POSTGRES_USER=crowded -e POSTGRES_PASSWORD="$(cat "${SECRETS}/DB_PASSWORD")" \
  postgres:18-alpine >/dev/null
docker run --detach --name "${RUN_ID}-rabbitmq" --network "${NETWORK}" --network-alias rabbitmq \
  -e RABBITMQ_DEFAULT_USER=crowded -e RABBITMQ_DEFAULT_PASS="$(cat "${SECRETS}/RABBITMQ_PASSWORD")" \
  rabbitmq:4.1-alpine >/dev/null
docker run --detach --name "${RUN_ID}-redis" --network "${NETWORK}" --network-alias redis \
  redis:8.2-alpine redis-server --requirepass "$(cat "${SECRETS}/REDIS_PASSWORD")" >/dev/null

docker run --detach --name "${RUN_ID}-app" --network "${NETWORK}" \
  -v "${SECRETS}:/run/secrets:ro" \
  -e DB_URL=jdbc:postgresql://postgres:5432/crowded -e DB_USERNAME=crowded \
  -e RABBITMQ_HOST=rabbitmq -e RABBITMQ_USERNAME=crowded \
  -e REDIS_HOST=redis \
  -e JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt_public_key \
  -e JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt_private_key \
  -e SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/ \
  "${IMAGE}" >/dev/null

# The image's HEALTHCHECK calls /actuator/health/readiness.
deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
while true; do
  status="$(docker inspect --format '{{.State.Status}} {{.State.Health.Status}}' "${RUN_ID}-app")"
  case "${status}" in
    "running healthy")
      # Readiness does not check the dependencies; the full health does, which
      # proves every secret file was read.
      if docker exec "${RUN_ID}-app" wget -q -O - http://127.0.0.1:8080/actuator/health \
        | grep -q '"status":"UP"'; then
        echo "${IMAGE} is healthy and reaches PostgreSQL, RabbitMQ and Redis."
        exit 0
      fi
      ;;
    exited* | dead* | *unhealthy)
      break
      ;;
  esac
  if (( $(date +%s) >= deadline )); then
    echo "Timed out after ${TIMEOUT_SECONDS}s." >&2
    break
  fi
  sleep 3
done

echo "${IMAGE} did not become healthy (${status}). Its log:" >&2
docker logs --tail 80 "${RUN_ID}-app" >&2
exit 1
