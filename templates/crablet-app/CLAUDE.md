# Crablet App Instructions

This is a generated Crablet application.

## Skill Routing

- App feature slices, command handlers, views, automations, outbox, codegen sequencing, and verification: invoke `/crablet-app-dev`.
- Greenfield pacing from app baseline through workshop, first slice, and evolving features: invoke `/crablet-greenfield`.
- Codegen provider config, artifact ownership, and recovery: invoke `/crablet-codegen`.
- DCB command patterns (`idempotent` / `commutative` / `non-commutative`), tags as the consistency boundary, `guardEvents`, and related consistency questions: invoke `/crablet-dcb`.
- Diagram metadata (`diagram.actors`, `diagram.lanes`, `diagram.assignments`, sidecar overlays): invoke `/crablet-diagram-advisor`.
- Crablet deployment topology, `make k8s`, generated `k8s/base`, KEDA, and singleton worker layout: invoke `/crablet-k8s`.
- Local codegen loop, Testcontainers, LISTEN/NOTIFY constraint, troubleshooting: invoke `/crablet-local-dev`.
- Event Modeling workshop dialogue and `event-model.yaml` shape: invoke `/crablet-event-modeling`.

## Essentials

- Work one vertical slice at a time.
- Ask for missing business facts before changing files.
- Update `event-model.yaml` before generated structural code.
- Run `crablet_plan` and review artifacts before `crablet_generate`.
- Generate with `output` set to `src/main/java`.
- Claude Code and Cursor can use MCP tools; Codex and terminal workflows should use `make plan`,
  `make generate`, and `make verify`.
- `plan` and `generate` are deterministic and do not call a model. Provider config is only needed for future opt-in commands (`crablet explain`, `crablet suggest`).
- Run `./mvnw verify` after generation or manual repair.
