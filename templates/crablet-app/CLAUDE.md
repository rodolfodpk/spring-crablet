# Crablet App Instructions

This is a generated Crablet application.

## Skill Routing

- App feature slices, command handlers, views, automations, outbox — hand-written Java: invoke `/crablet-app-dev`.
- Greenfield pacing from app baseline through first slice and evolving features: invoke `/crablet-greenfield`.
- DCB command patterns (`idempotent` / `commutative` / `non-commutative`), tags as the consistency boundary, `guardEvents`, and related consistency questions: invoke `/crablet-dcb`.
- Local build, Testcontainers, troubleshooting: invoke `/crablet-local-dev`.

Pré-1.0/experimental (AI-assisted codegen track — see the spring-crablet `docs/dev/PRODUCT_ROADMAP.md`):
- Event Modeling workshop dialogue and `event-model.yaml` shape: invoke `/crablet-event-modeling`.
- Codegen provider config, artifact ownership, and recovery: invoke `/crablet-codegen`.
- Diagram metadata (`diagram.actors`, `diagram.lanes`, `diagram.assignments`, sidecar overlays): invoke `/crablet-diagram-advisor`.
- Crablet deployment topology and singleton worker layout (pré-1.0/experimental): invoke `/crablet-k8s`.

## Essentials

- Work one vertical slice at a time.
- Ask for missing business facts before changing files.
- Write the command, event(s), handler, and (if needed) view/automation/outbox by hand.
- Write handler/view/automation tests.
- Run `./mvnw verify` after each change.
