#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

has_rg=0
if command -v rg >/dev/null 2>&1; then
  has_rg=1
fi

fail() {
  echo "docs-check: $*" >&2
  exit 1
}

contains() {
  local pattern="$1"
  local file="$2"

  if [ "$has_rg" -eq 1 ]; then
    rg -q "$pattern" "$file"
  else
    grep -Eq "$pattern" "$file"
  fi
}

print_matches() {
  local pattern="$1"
  shift

  if [ "$has_rg" -eq 1 ]; then
    rg -n "$pattern" "$@"
  else
    grep -En "$pattern" "$@"
  fi
}

check_links() {
  local missing=0
  local markdown_files=(
    "README.md"
    "crablet-commands/README.md"
    "crablet-event-poller/README.md"
    "crablet-eventstore/GETTING_STARTED.md"
    "crablet-views/README.md"
    "crablet-outbox/README.md"
    "crablet-automations/README.md"
    "wallet-example-app/README.md"
    "docs/AI_FIRST_WORKFLOW.md"
    "docs/FEATURE_SLICE_WORKFLOW.md"
    "docs/EVENT_MODEL_FORMAT.md"
    "docs/QUICKSTART.md"
    "docs/CREATE_A_CRABLET_APP.md"
    "docs/PERFORMANCE.md"
    "docs/TROUBLESHOOTING.md"
    "docs/LEARNING_MODE.md"
    "docs/COMMANDS_FIRST_ADOPTION.md"
    "docs/DEPLOYMENT_TOPOLOGY.md"
    "docs/TUTORIAL.md"
    "docs/DOCS_VERIFICATION.md"
    "docs/tutorials/01-event-store-basics.md"
    "docs/tutorials/02-commands.md"
    "docs/tutorials/03-dcb-consistency-boundaries.md"
    "docs/tutorials/04-views.md"
    "docs/tutorials/05-automations.md"
    "docs/tutorials/06-outbox.md"
  )

  local file=""
  for file in "${markdown_files[@]}"; do
    local dir=""
    dir="$(dirname "$file")"

    local match=""
    while IFS= read -r match; do
      [ -z "$match" ] && continue

      local target=""
      target="${match#*](}"
      target="${target%)}"
      target="${target#<}"
      target="${target%>}"
      target="${target%%#*}"

      case "$target" in
        ""|http://*|https://*|mailto:*|\#*)
          continue
          ;;
      esac

      if [ ! -e "$dir/$target" ]; then
        echo "docs-check: broken link in $file -> $target" >&2
        missing=1
      fi
    done < <(grep -oE '\[[^]]+\]\((<[^>]+>|[^)]+)\)' "$file" || true)
  done

  [ "$missing" -eq 0 ] || fail "link validation failed"
}

check_forbidden_phrases() {
  local scope=(
    "README.md"
    "docs/QUICKSTART.md"
    "docs/LEARNING_MODE.md"
    "docs/COMMANDS_FIRST_ADOPTION.md"
    "docs/DEPLOYMENT_TOPOLOGY.md"
    "docs/TUTORIAL.md"
    "docs/tutorials/04-views.md"
    "docs/tutorials/05-automations.md"
    "docs/tutorials/06-outbox.md"
    "crablet-event-poller/README.md"
    "crablet-views/README.md"
    "crablet-outbox/README.md"
    "crablet-automations/README.md"
    "wallet-example-app/README.md"
  )

  if print_matches "2 instances at most" "${scope[@]}" >/dev/null; then
    print_matches "2 instances at most" "${scope[@]}"
    fail "found outdated deployment wording"
  fi
}

check_required_phrase() {
  local phrase_regex="$1"
  local phrase_label="$2"
  shift
  shift

  local file=""
  for file in "$@"; do
    contains "$phrase_regex" "$file" || fail "$file must mention: $phrase_label"
  done
}

check_tutorial_import_context() {
  local tutorial_files=(
    "docs/tutorials/01-event-store-basics.md"
    "docs/tutorials/02-commands.md"
    "docs/tutorials/03-dcb-consistency-boundaries.md"
    "docs/tutorials/04-views.md"
    "docs/tutorials/05-automations.md"
  )

  local file=""
  for file in "${tutorial_files[@]}"; do
    contains "Assume this import in the snippets below:" "$file" || fail "$file must include import context for tutorial snippets"
    contains "EventType.type" "$file" || fail "$file must mention EventType.type"
  done
}

check_canonical_fixture_links() {
  local files=(
    "crablet-eventstore/GETTING_STARTED.md"
    "docs/tutorials/01-event-store-basics.md"
    "docs/tutorials/02-commands.md"
    "docs/tutorials/03-dcb-consistency-boundaries.md"
    "docs/tutorials/04-views.md"
    "docs/tutorials/05-automations.md"
    "docs/tutorials/06-outbox.md"
  )

  local file=""
  for file in "${files[@]}"; do
    contains "Canonical compile fixture:" "$file" || fail "$file must link to its canonical compile fixture"
    contains "docs-samples/src/main/java/com/crablet/docs/samples/tutorial/" "$file" || fail "$file must reference a docs-samples tutorial fixture"
  done
}

check_outbox_api_snippet() {
  local file="docs/tutorials/06-outbox.md"
  contains "publishBatch" "$file" || fail "$file must use publishBatch(...)"
  contains "isHealthy" "$file" || fail "$file must show isHealthy()"
}

check_runtime_doc_examples() {
  contains "createdb wallet_db" "docs/BUILD.md" || fail "docs/BUILD.md must show database creation for wallet-example-app"
  contains "createdb wallet_db" "wallet-example-app/README.md" || fail "wallet-example-app/README.md must show database creation"
  contains "lastUpdatedAt" "docs/QUICKSTART.md" || fail "docs/QUICKSTART.md must show the current wallet response shape"

  contains "wallet-transaction-view" "docs/MANAGEMENT_API.md" || fail "docs/MANAGEMENT_API.md must reference wallet-transaction-view"
  contains "wallet-opened-welcome-notification" "docs/MANAGEMENT_API.md" || fail "docs/MANAGEMENT_API.md must reference the current automation name"

  if print_matches "/actuator/outbox" "crablet-outbox/README.md" "crablet-outbox/docs/OUTBOX_PATTERN.md" >/dev/null; then
    print_matches "/actuator/outbox" "crablet-outbox/README.md" "crablet-outbox/docs/OUTBOX_PATTERN.md"
    fail "found outdated outbox management endpoints"
  fi

  if print_matches "publish\\(String topic, List<StoredEvent> events\\)" "crablet-outbox/README.md" >/dev/null; then
    print_matches "publish\\(String topic, List<StoredEvent> events\\)" "crablet-outbox/README.md"
    fail "found outdated OutboxPublisher API shape"
  fi

  if print_matches "processor_progress" "crablet-event-poller/README.md" >/dev/null; then
    print_matches "processor_progress" "crablet-event-poller/README.md"
    fail "found outdated generic progress table name"
  fi

  if print_matches "/actuator/processor" "crablet-event-poller/README.md" >/dev/null; then
    print_matches "/actuator/processor" "crablet-event-poller/README.md"
    fail "found outdated processor management endpoints"
  fi
}

check_links
check_forbidden_phrases
check_required_phrase "(1|one) application instance per cluster" "1 application instance per cluster" \
  "README.md" \
  "docs/DEPLOYMENT_TOPOLOGY.md" \
  "docs/TUTORIAL.md" \
  "crablet-event-poller/README.md" \
  "crablet-views/README.md" \
  "crablet-outbox/README.md" \
  "crablet-automations/README.md"
check_required_phrase "singleton worker service per poller-backed module|one active poller per (poller-backed )?module" "singleton worker service per poller-backed module or one active poller per module" \
  "README.md" \
  "docs/DEPLOYMENT_TOPOLOGY.md" \
  "docs/TUTORIAL.md" \
  "crablet-event-poller/README.md"
check_tutorial_import_context
check_canonical_fixture_links
check_outbox_api_snippet
check_runtime_doc_examples

echo "docs-check: OK"
