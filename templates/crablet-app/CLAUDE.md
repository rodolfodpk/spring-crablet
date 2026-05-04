# Crablet App Instructions

This is a generated Crablet application.

## Skill Routing

- App feature slices, command handlers, views, automations, outbox, codegen sequencing, and verification: use `.claude/skills/crablet-app-dev/SKILL.md`.
- Event Modeling workshop dialogue and `event-model.yaml` shape: use `.claude/skills/event-modeling/SKILL.md`.
- Diagram metadata (`diagram.actors`, `diagram.lanes`, `diagram.assignments`, sidecar overlays): use `.claude/skills/crablet-diagram-advisor/SKILL.md`.

## Essentials

- Work one vertical slice at a time.
- Ask for missing business facts before changing files.
- Update `event-model.yaml` before generated structural code.
- Run `embabel_plan` and review artifacts before `embabel_generate`.
- Generate with `output` set to `src/main/java`.
- Claude Code and Cursor can use MCP tools; Codex and terminal workflows should use `make plan`,
  `make generate`, and `make verify`.
- `plan` is deterministic and does not call a model. `generate` uses the configured provider.
- Run `./mvnw verify` after generation or manual repair.
