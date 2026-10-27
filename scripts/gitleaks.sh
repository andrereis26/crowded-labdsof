#!/bin/sh
# Runs gitleaks at the version the Security workflow uses: the installed binary
# if it is that version, otherwise the official Docker image.
# Usage: scripts/gitleaks.sh <gitleaks arguments>
#   e.g. scripts/gitleaks.sh git --pre-commit --staged --redact
set -eu

version=8.30.1
image="zricethezav/gitleaks:v$version"

if command -v gitleaks >/dev/null 2>&1 && [ "$(gitleaks version | sed 's/^v//')" = "$version" ]; then
  exec gitleaks "$@"
fi

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  # Mount the work tree and the Git directory at their own paths, so Git
  # commands inside the container (and linked worktrees) see the same paths.
  top=$(git rev-parse --show-toplevel)
  git_dir=$(cd "$(git rev-parse --git-common-dir)" && pwd)
  exec docker run --rm --user "$(id -u):$(id -g)" \
    -v "$top:$top" -v "$git_dir:$git_dir" -w "$PWD" \
    -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0='*' \
    "$image" "$@"
fi

echo "gitleaks $version is needed: install it (https://github.com/gitleaks/gitleaks#installing) or start Docker." >&2
exit 1
