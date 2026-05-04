# Crablet Agent Guidance — Revised Implementation Plan

## Context

The original plan proposed adding a Crablet-native agent layer alongside the existing skills.
On evaluation, the proposed agents substantially overlapped existing skills (`event-modeling`,
`dcb`, `crablet-app-dev`, `crablet-maintainer`). The revised approach resolves agent content into
the skills system: overlapping scopes are absorbed into existing skills, and only the distinct
diagram-renderer scope becomes a new skill.

## Non-Goals (unchanged)

- Do not install or vendor `melodic-software/claude-code-plugins`.
- Do not replace the existing `event-modeling`, `dcb`, `crablet-app-dev`, or
  `crablet-maintainer` skills.
- Do not introduce generic saga state machines as a Crablet runtime concept.
- Do not move external publication into command handlers or automations.

## What Was Dropped and Why

The proposed `crablet-event-modeler`, `crablet-dcb-advisor`, `crablet-process-advisor`, and
`crablet-codegen-advisor` agents are not created as separate skill files. Their content is
fully absorbed into the enhanced existing skills, which avoids routing duplication and keeps
`CLAUDE.md`'s four-skill routing hub coherent.

The proposed `crablet-diagram-advisor` is kept: the renderer's rules (arrow strokes, actor
bands, canonical vs sidecar overlays) have no existing home and are genuinely distinct scope.

## Implementation Shape

### New skill files

| File | Scope |
|---|---|
| `.claude/skills/crablet-diagram-advisor/SKILL.md` | Root / maintainer-facing: renderer arrow rules, actor-band vocabulary, sidecar overlay policy, multi-lane board authoring, renderer internals |
| `templates/crablet-app/.claude/skills/crablet-diagram-advisor/SKILL.md` | Lightweight app-user version: `diagram.actors`, `diagram.lanes`, `diagram.assignments`, sidecar vs main model distinction, "Java codegen ignores `diagram:`"; omits renderer internals |

### Skills enhanced (existing files)

| File | What was added |
|---|---|
| `.claude/skills/event-modeling/SKILL.md` | Facilitation aids: brain dump, timeline arrangement, hot spots, Given/When/Then slice summaries, saga-as-TODO/automation/command/event pattern |
| `.claude/skills/crablet-app-dev/SKILL.md` | Explicit 6-step Process Rule for automation/outbox workflows; cross-link to `dcb` for command-pattern choices |
| `.claude/skills/dcb/SKILL.md` | Cross-links to process guidance for automation retries and outbox for external publication |
| `.claude/skills/crablet-maintainer/SKILL.md` | Root vs template copy policy |

### Template mirrors updated

| File | What was mirrored |
|---|---|
| `templates/crablet-app/.claude/skills/event-modeling/SKILL.md` | Facilitation aids (user-facing subset); renderer/docs-maintainer vocabulary omitted |
| `templates/crablet-app/.claude/skills/crablet-app-dev/SKILL.md` | Process Rule section; framework-maintainer caveats omitted |

### Routing files updated

- `CLAUDE.md` — added `crablet-diagram-advisor` routing entry
- `templates/crablet-app/CLAUDE.md` — added `crablet-diagram-advisor` routing entry

## Verification

1. Open each modified SKILL.md and confirm new sections are present and consistent.
2. Use the `event-modeling` skill; confirm facilitation aids appear before Workshop Steps
   and the 6-step saga pattern is visible.
3. Use the `dcb` skill on an automation scenario; confirm cross-links appear.
4. Run the banned-pattern search:
   ```
   rg -n "validation:|notBlank|greaterThan|genericSaga|webhook.*automation" \
     .claude/skills \
     templates/crablet-app/.claude/skills
   ```
   Matches inside "Do NOT use" warnings are acceptable; positive examples or instructions are bugs.
5. Use each template skill and confirm output is app-user-focused with no renderer internals,
   datasource rules, or codegen adapter details.
6. Confirm `crablet-diagram-advisor` routing entries appear in both `CLAUDE.md` files.
