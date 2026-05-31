#!/usr/bin/env bash
#
# PreToolUse(Bash) guard: block `mvnw ... -pl` invocations that omit `-am`.
#
# Direct `./mvnw test -pl <module>` without `-am` can resolve a stale locally
# installed sibling SNAPSHOT and run tests against old Flyway migrations
# (see CLAUDE.md > Build Commands). This hook blocks that footgun before it runs.
#
# Advisory: if `jq` is not installed the hook is a no-op (fails open) so it can
# never wedge developer flow. CI remains the real gate.
set -euo pipefail
export LC_ALL=C

command -v jq >/dev/null 2>&1 || exit 0

input="$(cat)"
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // ""')"

# Let `make ...` wrappers through — make test-pl already adds -am internally.
case "$cmd" in
  *make\ *) exit 0 ;;
esac

# Already correct: anything using -am / --also-make is fine.
if printf '%s' "$cmd" | grep -Eq -- '(^|[[:space:]])-am([[:space:]]|$)|--also-make'; then
  exit 0
fi

# Block direct mvnw/mvn invocations that scope with -pl/--projects but omit -am.
if printf '%s' "$cmd" | grep -Eq -- 'mvnw|(^|[[:space:]])mvn([[:space:]]|$)' \
   && printf '%s' "$cmd" | grep -Eq -- '(^|[[:space:]])-pl([[:space:]]|=)|--projects'; then
  echo "Blocked: 'mvnw ... -pl' without '-am' can resolve a stale SNAPSHOT sibling" >&2
  echo "and run tests against old Flyway migrations." >&2
  echo "Use 'make test-pl PL=<module>', or add '-am'. See CLAUDE.md > Build Commands." >&2
  exit 2
fi

exit 0
