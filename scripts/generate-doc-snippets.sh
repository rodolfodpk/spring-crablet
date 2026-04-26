#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:-write}"
OUT_DIR="${2:-docs/user/generated}"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

extract_markers() {
  local file="$1"
  local current=""
  local out=""

  while IFS= read -r line; do
    case "$line" in
      *"// docs:begin "*)
        current="${line##*// docs:begin }"
        out="$OUT_DIR/${current}.java"
        : > "$out"
        ;;
      *"// docs:end "*)
        current=""
        out=""
        ;;
      *)
        if [ -n "$current" ]; then
          printf '%s\n' "$line" >> "$out"
        fi
        ;;
    esac
  done < "$file"
}

while IFS= read -r file; do
  extract_markers "$file"
done < <(find docs-samples/src/main/java -name '*.java' | sort)

if [ "$MODE" = "check" ]; then
  if ! diff -ru docs/user/generated "$OUT_DIR" >/dev/null; then
    diff -ru docs/user/generated "$OUT_DIR" || true
    echo "docs-generate-check: generated snippets are out of date" >&2
    exit 1
  fi
  rm -rf "$OUT_DIR"
  echo "docs-generate-check: OK"
else
  echo "docs-generate: wrote snippets to $OUT_DIR"
fi
