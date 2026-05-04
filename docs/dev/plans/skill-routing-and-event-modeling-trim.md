# Skill Routing Cleanup and event-modeling Trim

## Context

Three improvements identified during a Claude artifact review:

1. `event-modeling` carries ~23 lines of renderer/arrow vocabulary that now belongs in
   `crablet-diagram-advisor`, which already exists in both root and template.
2. `event-modeling` step 6 (Deployment Topology) is ~45 verbose lines; the YAML keys must stay
   but the prose can be compressed with a pointer to the existing `DEPLOYMENT_TOPOLOGY.md` doc.
3. `CLAUDE.md` routing entries used file paths (`use .claude/skills/X/SKILL.md`) instead of
   slash commands; five generic root skills were installed but not mentioned in any routing section.

## Changes Applied

### 1 — Removed "Canonical HTML diagram" from `event-modeling`

The `### Canonical HTML diagram` subsection was removed from `.claude/skills/event-modeling/SKILL.md`.
That vocabulary (arrow strokes, actor bands, header legend, outbox edges) is now owned entirely
by `.claude/skills/crablet-diagram-advisor/SKILL.md`. The `## Event Modeling Board Semantics`
section now ends with a pointer to `/crablet-diagram-advisor`.

Also updated:
- `CLAUDE.md` signpost (routing section) — removed "§ Canonical HTML diagram" reference
- `CLAUDE.md` Repo Conventions line 110 — now points to `/crablet-diagram-advisor`

### 2 — Compressed Deployment Topology in `event-modeling`

Step 6 was reduced from ~45 lines to ~18 lines. All YAML keys
(`deployment.topology`, `deployment.commandReplicas`, `deployment.keda.*`) remain present.
The monolith-with-poller-backed-modules operational warning is preserved. Full trade-off
prose deferred to `docs/user/DEPLOYMENT_TOPOLOGY.md`.

### 3 — CLAUDE.md routing now uses slash commands

All routing entries changed from `use .claude/skills/X/SKILL.md` to `invoke /X`.
Root `CLAUDE.md` adds an "Other tools" section listing the five generic root skills:
`/balanced-coupling`, `/design`, `/review`, `/document`, `/jspecify-skill`.
Template `CLAUDE.md` updated to slash-command form; no "Other tools" section (generic
tools are not relevant to app users).

## Verification

```
rg -n "use `\.claude/skills/.*/SKILL\.md`|§ Canonical HTML diagram|Canonical HTML diagram" \
  CLAUDE.md templates/crablet-app/CLAUDE.md .claude/skills/event-modeling/SKILL.md
```

Expected: zero results.
