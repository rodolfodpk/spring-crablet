#!/usr/bin/env bash
#
# Template skill coverage check for templates/crablet-app/.
#
# This script detects drift/missing artifacts in the STARTER template (expected skills,
# frontmatter ids, gist markers per skill). It does NOT enforce byte equality against
# the spring-crablet repo root .claude/skills/ tree root skills may deliberately differ.
#

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_ROOT="${ROOT}/templates/crablet-app"
SKILLS="${TEMPLATE_ROOT}/.claude/skills"
TEMPLATE_CLAUDE="${TEMPLATE_ROOT}/CLAUDE.md"
TOOLS="${TEMPLATE_ROOT}/tools"

die() {
  printf 'check-template-skills: ERROR: %s\n' "$*" >&2
  exit 1
}

require_name_frontmatter() {
  local skill_id="$1"
  local file="$2"
  grep -qE '^[[:space:]]*name:[[:space:]]+'"${skill_id}"'[[:space:]]*$' "$file" \
    || die "${file} must contain YAML frontmatter line: name: ${skill_id}"
}

grep_markers() {
  local file="$1"
  shift
  local marker
  for marker in "$@"; do
    grep -qiF -- "$marker" "$file" \
      || die "${file} missing marker (case-insensitive fixed string): ${marker}"
  done
}

SKILL_IDS=(
  event-modeling
  crablet-dcb
  crablet-greenfield
  crablet-app-dev
  crablet-codegen
  crablet-local-dev
  crablet-diagram-advisor
  crablet-k8s
)

for id in "${SKILL_IDS[@]}"; do
  path="${SKILLS}/${id}/SKILL.md"
  [[ -f "$path" ]] || die "missing skill file: ${path}"
  require_name_frontmatter "$id" "$path"
done

grep_markers "${SKILLS}/event-modeling/SKILL.md" \
  'event-model.yaml' \
  'Given/When/Then' \
  'Policies in Crablet'

grep_markers "${SKILLS}/crablet-dcb/SKILL.md" \
  'idempotent' \
  'commutative' \
  'non-commutative' \
  'guardEvents'

grep_markers "${SKILLS}/crablet-greenfield/SKILL.md" \
  'Phase A' \
  'make diagram-preview' \
  'crablet-app-dev'

grep_markers "${SKILLS}/crablet-app-dev/SKILL.md" \
  'make plan' \
  'make generate' \
  'make verify' \
  'make diagram-preview'

grep_markers "${SKILLS}/crablet-codegen/SKILL.md" \
  'Regeneration behavior' \
  'RepairAgent' \
  'CODEGEN_LLM_PROVIDER'

grep_markers "${SKILLS}/crablet-local-dev/SKILL.md" \
  'MCP' \
  'Testcontainers' \
  'LISTEN/NOTIFY'

grep_markers "${SKILLS}/crablet-diagram-advisor/SKILL.md" \
  'diagram.actors' \
  'diagram.lanes' \
  'Java codegen ignores'

grep_markers "${SKILLS}/crablet-k8s/SKILL.md" \
  'deployment.topology' \
  'KEDA' \
  'k8s/base'

[[ -f "${TOOLS}/diagram-preview.js" ]] || die "missing ${TOOLS}/diagram-preview.js"
[[ -f "${TOOLS}/event-model-renderer.js" ]] || die "missing ${TOOLS}/event-model-renderer.js"
[[ -f "${TOOLS}/package.json" ]] || die "missing ${TOOLS}/package.json"

grep_markers "${TEMPLATE_ROOT}/Makefile" \
  'diagram-preview:' \
  'tools/diagram-preview.js'

grep_markers "${TOOLS}/diagram-preview.js" \
  'event-model.yaml' \
  'diagram-preview.html' \
  'EventModelRenderer.render'

grep_markers "${TOOLS}/package.json" \
  'js-yaml'

cmp -s "${ROOT}/docs/event-model-renderer.js" "${TOOLS}/event-model-renderer.js" \
  || die "${TOOLS}/event-model-renderer.js must match docs/event-model-renderer.js"

[[ -f "${TEMPLATE_CLAUDE}" ]] || die "missing ${TEMPLATE_CLAUDE}"

for id in "${SKILL_IDS[@]}"; do
  needle="/${id}"
  grep -qF -- "${needle}" "${TEMPLATE_CLAUDE}" \
    || die "${TEMPLATE_CLAUDE} missing routing substring: ${needle}"
done

printf 'check-template-skills: OK (%d skills + CLAUDE routing).\n' "${#SKILL_IDS[@]}"
