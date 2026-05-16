#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C
export LANG=C

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="$ROOT_DIR/crablet-test-support/src/main/resources/db/migration"
ARTIFACT="$HOME/.m2/repository/com/crablet/crablet-test-support/1.0.0-SNAPSHOT/crablet-test-support-1.0.0-SNAPSHOT.jar"

if [[ ! -f "$ARTIFACT" ]]; then
  echo "crablet-test-support artifact is missing: $ARTIFACT" >&2
  echo "Run: make build-test-support" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

find "$SOURCE_DIR" -maxdepth 1 -type f -name 'V*.sql' -print \
  | sed "s#^$SOURCE_DIR/##" \
  | sort > "$tmp_dir/source-files"

jar tf "$ARTIFACT" \
  | grep '^db/migration/V.*\.sql$' \
  | sed 's#^db/migration/##' \
  | sort > "$tmp_dir/artifact-files"

if ! diff -u "$tmp_dir/source-files" "$tmp_dir/artifact-files" > "$tmp_dir/file-diff"; then
  echo "crablet-test-support artifact migration list is stale." >&2
  cat "$tmp_dir/file-diff" >&2
  echo "Run: make build-test-support" >&2
  exit 1
fi

while IFS= read -r file; do
  jar_hash="$(unzip -p "$ARTIFACT" "db/migration/$file" | shasum -a 256 | awk '{print $1}')"
  source_hash="$(shasum -a 256 "$SOURCE_DIR/$file" | awk '{print $1}')"
  if [[ "$jar_hash" != "$source_hash" ]]; then
    echo "crablet-test-support artifact is stale for db/migration/$file." >&2
    echo "Run: make build-test-support" >&2
    exit 1
  fi
done < "$tmp_dir/source-files"

echo "✓ crablet-test-support artifact matches source migrations."
