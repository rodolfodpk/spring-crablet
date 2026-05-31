#!/usr/bin/env bash
#
# PostToolUse(Edit|Write) guard: warn when an edited file reintroduces a banned term.
#
# Warning-only by design: PostToolUse fires after the edit has already landed, so
# this hook can only feed feedback back to the agent, never block. Use it to catch
# stale references (e.g. the renamed embabel-codegen module) at edit time.
#
# Advisory: no-op without `jq`.
set -euo pipefail
export LC_ALL=C

# Case-insensitive extended-regex of terms that should no longer appear in new edits.
BANNED_TERMS="embabel"

command -v jq >/dev/null 2>&1 || exit 0

input="$(cat)"
file="$(printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_input.path // ""')"
[ -n "$file" ] && [ -f "$file" ] || exit 0

# Never flag the hook scripts themselves, nor the superseded historical plans
# (which legitimately reference the old name — see the message below).
case "$file" in
  */scripts/hooks/*) exit 0 ;;
  */docs/dev/plans/*) exit 0 ;;
esac

# Per-file opt-out: a file may legitimately name a banned term (e.g. a skill that
# documents the "no embabel" rule) by including this marker anywhere in its body.
if grep -q "crablet-banned-terms: allow" "$file" 2>/dev/null; then
  exit 0
fi

if grep -inE "$BANNED_TERMS" "$file" >/dev/null 2>&1; then
  echo "Warning: '$file' contains a banned term (${BANNED_TERMS})." >&2
  echo "embabel-codegen was renamed to crablet-codegen and Embabel is no longer used." >&2
  echo "The superseded plans under docs/dev/plans/ are the only intended exceptions." >&2
  exit 2
fi

exit 0
