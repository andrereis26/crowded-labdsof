#!/bin/sh
# Formats the given Java files of a backend service with Spotless.
# Lefthook's pre-commit runs it from the service folder (core/, jobs/,
# notifications/) with the staged files, relative to that folder:
#   ../scripts/spotless-staged.sh ./src/main/java/.../Foo.java ...
# The logic lives here because Lefthook on Windows drops the quotes and ${...}
# of an inline run command. POSIX sh, so it runs on macOS's bash 3.2 too.
set -eu

[ "$#" -gt 0 ] || exit 0

# Spotless matches each pattern against the file's absolute path, which uses
# backslashes on Windows: "." matches either separator.
files=""
for f in "$@"; do
  f=${f#./}
  files="$files,.*$(printf '%s' "$f" | sed 's|/|.|g')"
done

exec ./mvnw -B -ntp -q spotless:apply -DspotlessFiles="${files#,}"
