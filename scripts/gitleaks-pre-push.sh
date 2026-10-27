#!/bin/sh
# Pre-push hook: scans the commits being pushed for secrets.
# Git passes the remote name as $1 and one line per pushed ref on stdin:
#   <local ref> <local sha> <remote ref> <remote sha>
set -eu

remote="$1"
zero=0000000000000000000000000000000000000000
status=0

while read -r _local_ref local_sha _remote_ref remote_sha; do
  # A deleted branch pushes no commits.
  [ "$local_sha" = "$zero" ] && continue
  if [ "$remote_sha" = "$zero" ]; then
    # New branch: the commits that are not on the remote yet.
    range="$local_sha --not --remotes=$remote"
  else
    range="$remote_sha..$local_sha"
  fi
  "$(dirname "$0")/gitleaks.sh" git --log-opts="$range" --redact --verbose --no-banner || status=1
done

exit $status
