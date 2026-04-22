# Crablet App Instructions

This is a Crablet application. Work one vertical slice at a time.

When adding a feature:

1. Ask for missing business facts before changing files.
2. Update `event-model.yaml` first.
3. Keep the slice scoped to one observable user outcome.
4. Run `embabel_plan` and show the planned artifacts.
5. Ask for confirmation before running `embabel_generate`.
6. Run `./mvnw verify` after generation.
7. Prefer improving `event-model.yaml` over hand-patching generated structural code.

For each slice, clarify:

- command name, fields, and validation
- event name, fields, and tags
- command pattern: `idempotent`, `commutative`, or `non-commutative`
- consistency checks and guard events
- read model needed to observe the result
- automation or outbox behavior, if needed
- sample scenario used to verify the slice

Generated applications target the Crablet runtime modules:

- `crablet-eventstore`
- `crablet-commands`
- `crablet-commands-web`
- `crablet-views`
- `crablet-automations`
- `crablet-outbox`

Do not generate code until the artifact plan has been reviewed.
