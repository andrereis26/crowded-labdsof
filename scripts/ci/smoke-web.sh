#!/usr/bin/env bash
# Smoke test of a web image (participant app, backoffice): the container
# answers its health check and serves the app's HTML on every path given.
# Usage: scripts/ci/smoke-web.sh <image> <path>...

set -euo pipefail

IMAGE="${1:?usage: smoke-web.sh <image> <path>...}"
shift
NAME="smoke-web-$$"
trap 'docker rm --force "${NAME}" >/dev/null 2>&1 || true' EXIT

docker run --detach --name "${NAME}" --publish 127.0.0.1::8080 "${IMAGE}" >/dev/null
port="$(docker port "${NAME}" 8080/tcp | head -n1 | sed 's/.*://')"
base="http://127.0.0.1:${port}"

for _ in $(seq 1 20); do
  curl --silent --fail "${base}/healthz" >/dev/null && break
  sleep 1
done
curl --silent --show-error --fail "${base}/healthz" >/dev/null

for path in "$@"; do
  if ! curl --silent --show-error --fail "${base}${path}" | grep -q '<title>'; then
    echo "${path} did not return the app's HTML." >&2
    docker logs --tail 40 "${NAME}" >&2
    exit 1
  fi
done
echo "${IMAGE} serves /healthz and $*."
