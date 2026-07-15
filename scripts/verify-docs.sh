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
  local file=""

  while IFS= read -r file; do
    is_generic_third_party_skill "$file" && continue

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
  done < <(git ls-files '*.md')

  [ "$missing" -eq 0 ] || fail "link validation failed"
}

check_forbidden_phrases() {
  local scope=(
    "README.md"
    "docs/user/QUICKSTART.md"
    "docs/user/LEARNING_MODE.md"
    "docs/user/COMMANDS_FIRST_ADOPTION.md"
    "docs/user/DEPLOYMENT_TOPOLOGY.md"
    "docs/user/TUTORIAL.md"
    "docs/user/tutorials/04-views.md"
    "docs/user/tutorials/05-automations.md"
    "docs/user/tutorials/06-outbox.md"
    "crablet-event-poller/README.md"
    "crablet-views/README.md"
    "crablet-outbox/README.md"
    "crablet-automations/README.md"
    "examples/wallet-example-app/README.md"
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
    "docs/user/tutorials/01-event-store-basics.md"
    "docs/user/tutorials/02-commands.md"
    "docs/user/tutorials/03-dcb-consistency-boundaries.md"
    "docs/user/tutorials/04-views.md"
    "docs/user/tutorials/05-automations.md"
  )

  local file=""
  for file in "${tutorial_files[@]}"; do
    contains "Assume this import in the snippets below:" "$file" || fail "$file must include import context for tutorial snippets"
    contains "EventType.type" "$file" || fail "$file must mention EventType.type"
  done
}

check_canonical_fixture_links() {
  local file=""
  for file in "crablet-eventstore/GETTING_STARTED.md" docs/user/tutorials/*.md; do
    [ -f "$file" ] || continue
    contains "Canonical compile fixture:" "$file" || fail "$file must link to its canonical compile fixture"
    contains "docs-samples/src/main/java/com/crablet/docs/samples/tutorial/" "$file" || fail "$file must reference a docs-samples tutorial fixture"
  done
}

# --- Word-budget guardrail ---------------------------------------------

# Ceilings set with modest headroom above the post-reduction baseline (208k -> ~85k words,
# ~59% cut) once the pré-1.0/experimental renderer chain was restored alongside crablet-codegen
# and crablet-k8s. These bound future growth; they are not meant to be shaved to the exact
# current count.
PUBLIC_BUDGET=60000
MAINTAINER_BUDGET=5000
AGENTS_BUDGET=30000
TOTAL_BUDGET=90000

# Exhaustive, exclusive precedence: first matching case branch wins, the
# trailing "*" catch-all guarantees every tracked Markdown file lands in
# exactly one category.
classify_doc() {
  case "$1" in
    docs/dev/*) echo "maintainer" ;;
    CLAUDE.md|*/CLAUDE.md) echo "agents" ;;
    .claude/*|.agents/*) echo "agents" ;;
    templates/crablet-app/.claude/skills/*) echo "agents" ;;
    *) echo "public" ;;
  esac
}

check_word_budget() {
  local total=0 maint=0 agents=0 public=0
  local file="" words="" category=""

  while IFS= read -r file; do
    words="$(wc -w < "$file")"
    total=$((total + words))
    category="$(classify_doc "$file")"
    case "$category" in
      maintainer) maint=$((maint + words)) ;;
      agents) agents=$((agents + words)) ;;
      public) public=$((public + words)) ;;
      *) fail "internal error: $file did not classify into a budget area" ;;
    esac
  done < <(git ls-files '*.md')

  echo "docs-check: word counts — public/modules=$public/$PUBLIC_BUDGET, maintainer=$maint/$MAINTAINER_BUDGET, agents=$agents/$AGENTS_BUDGET, total=$total/$TOTAL_BUDGET"

  local over=0
  [ "$public" -le "$PUBLIC_BUDGET" ] || { echo "docs-check: public/modules docs exceed budget: $public > $PUBLIC_BUDGET" >&2; over=1; }
  [ "$maint" -le "$MAINTAINER_BUDGET" ] || { echo "docs-check: maintainer docs exceed budget: $maint > $MAINTAINER_BUDGET" >&2; over=1; }
  [ "$agents" -le "$AGENTS_BUDGET" ] || { echo "docs-check: agent/skill docs exceed budget: $agents > $AGENTS_BUDGET" >&2; over=1; }
  [ "$total" -le "$TOTAL_BUDGET" ] || { echo "docs-check: total docs exceed budget: $total > $TOTAL_BUDGET" >&2; over=1; }
  [ "$over" -eq 0 ] || fail "word budget exceeded"
}

# --- Banned AI/Kubernetes promotional terms -----------------------------
#
# AI-first codegen and Kubernetes generation are pré-1.0/experimental. They
# may only be described as ready-to-use in docs/dev/PRODUCT_ROADMAP.md, in
# the four Crablet skills whose whole subject is that track (marked
# pré-1.0/experimental in their own body), and in generic third-party
# skills that aren't Crablet product docs at all.

is_roadmap_doc() {
  case "$1" in
    docs/dev/PRODUCT_ROADMAP.md|docs/examples/concepts.md)
      # concepts.md is the mind-map data source rendered by the pré-1.0/experimental
      # concepts.html viewer — it necessarily names the tracks it maps, same as the roadmap.
      return 0 ;;
    *) return 1 ;;
  esac
}

is_ai_exempt_skill() {
  case "$1" in
    .claude/skills/crablet-event-modeling/SKILL.md| \
    .claude/skills/crablet-codegen/SKILL.md| \
    .claude/skills/crablet-diagram-advisor/SKILL.md| \
    .claude/skills/crablet-k8s/SKILL.md| \
    templates/crablet-app/.claude/skills/crablet-event-modeling/SKILL.md| \
    templates/crablet-app/.claude/skills/crablet-codegen/SKILL.md| \
    templates/crablet-app/.claude/skills/crablet-diagram-advisor/SKILL.md| \
    templates/crablet-app/.claude/skills/crablet-k8s/SKILL.md)
      return 0 ;;
    *) return 1 ;;
  esac
}

is_generic_third_party_skill() {
  case "$1" in
    .claude/skills/balanced-coupling/*| \
    .claude/skills/design/*| \
    .claude/skills/document/*| \
    .claude/skills/jspecify/*| \
    .claude/skills/postgres-best-practices/*| \
    .claude/skills/review/*| \
    .claude/skills/skill-creator/*| \
    .agents/skills/skill-creator/*)
      return 0 ;;
    *) return 1 ;;
  esac
}

is_generic_infra_doc() {
  # Documents that describe running Crablet ON Kubernetes/an orchestrator as one deployment
  # option among several (portability fact), not documents that promote Crablet's own
  # pré-1.0/experimental manifest-generation feature as ready.
  case "$1" in
    crablet-event-poller/README.md| \
    crablet-eventstore/docs/CONNECTION_POOLERS.md| \
    crablet-outbox/docs/OUTBOX_METRICS.md| \
    crablet-outbox/docs/OUTBOX_PATTERN.md| \
    docs/user/LEADER_ELECTION.md)
      return 0 ;;
    *) return 1 ;;
  esac
}

check_banned_ai_kubernetes_terms() {
  local pattern='AI-first|\bLLM\b|Kubernetes|\bK8s\b|\bHelm\b|\bKEDA\b'
  local exempt_line_pattern='pré-1\.0|experimental|PRODUCT_ROADMAP'
  local violations=0
  local file="" hit="" filtered=""

  while IFS= read -r file; do
    is_roadmap_doc "$file" && continue
    is_ai_exempt_skill "$file" && continue
    is_generic_third_party_skill "$file" && continue
    is_generic_infra_doc "$file" && continue

    if [ "$has_rg" -eq 1 ]; then
      hit="$(rg -ni "$pattern" "$file" || true)"
    else
      hit="$(grep -Eni "$pattern" "$file" || true)"
    fi
    [ -z "$hit" ] && continue

    # Drop lines that already carry the pré-1.0/experimental/roadmap disclaimer inline.
    filtered="$(printf '%s\n' "$hit" | grep -Eiv "$exempt_line_pattern" || true)"

    if [ -n "$filtered" ]; then
      echo "docs-check: banned AI/Kubernetes promotional term outside roadmap in $file:" >&2
      echo "$filtered" >&2
      violations=1
    fi
  done < <(git ls-files '*.md')

  [ "$violations" -eq 0 ] || fail "banned AI/Kubernetes terms found outside docs/dev/PRODUCT_ROADMAP.md and exempt skills"
}

check_outbox_api_snippet() {
  local file="docs/user/tutorials/06-outbox.md"
  contains "publishBatch" "$file" || fail "$file must use publishBatch(...)"
  contains "isHealthy" "$file" || fail "$file must show isHealthy()"
}

check_runtime_doc_examples() {
  contains "createdb wallet_db" "docs/user/BUILD.md" || fail "docs/user/BUILD.md must show database creation for wallet-example-app"
  contains "createdb wallet_db" "examples/wallet-example-app/README.md" || fail "examples/wallet-example-app/README.md must show database creation"
  contains "lastUpdatedAt" "docs/user/QUICKSTART.md" || fail "docs/user/QUICKSTART.md must show the current wallet response shape"

  contains "wallet-transaction-view" "docs/user/MANAGEMENT_API.md" || fail "docs/user/MANAGEMENT_API.md must reference wallet-transaction-view"
  contains "wallet-opened-welcome-notification" "docs/user/MANAGEMENT_API.md" || fail "docs/user/MANAGEMENT_API.md must reference the current automation name"

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
  "docs/user/DEPLOYMENT_TOPOLOGY.md" \
  "docs/user/TUTORIAL.md" \
  "crablet-event-poller/README.md" \
  "crablet-views/README.md" \
  "crablet-outbox/README.md" \
  "crablet-automations/README.md"
check_required_phrase "singleton worker service per poller-backed module|one active poller per (poller-backed )?module" "singleton worker service per poller-backed module or one active poller per module" \
  "README.md" \
  "docs/user/DEPLOYMENT_TOPOLOGY.md" \
  "docs/user/TUTORIAL.md" \
  "crablet-event-poller/README.md"
check_tutorial_import_context
check_canonical_fixture_links
check_outbox_api_snippet
check_runtime_doc_examples
check_banned_ai_kubernetes_terms
check_word_budget

echo "docs-check: OK"
