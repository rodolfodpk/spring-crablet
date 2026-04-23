# Event Modeling

Use this skill when the user wants to add or change a Crablet feature slice.

## Workflow

1. Ask for the user-visible outcome.
2. Keep the scope to one vertical slice.
3. Identify commands, events, tags, validations, read models, automations, and outbox publishers.
4. Ask clarifying questions before editing `event-model.yaml`.
5. Update `event-model.yaml` only after the missing facts are known.
6. Run `embabel_plan` and show the planned artifacts.
7. Ask for confirmation before running `embabel_generate`.
8. Run `./mvnw verify` after generation.

## Required Questions

For each command:

- What is the command name?
- Which fields are required?
- Which validation rules apply?
- Is the command `idempotent`, `commutative`, or `non-commutative`?
- Which events can it produce?
- Which tags define the consistency boundary?

For each view:

- Which events update the view?
- Which tag identifies the row or lookup key?
- Which fields should be materialized?

For each automation or outbox publisher:

- Which event triggers it?
- Which condition must pass?
- Which command is emitted, or which external topic is published?
- Which adapter boundary should application code own?

## Rules

- Do not invent credentials, endpoints, or adapter details.
- Do not generate code before reviewing the artifact plan.
- Prefer improving `event-model.yaml` over hand-editing generated structural code.
- Keep generated slices small enough to verify with one command and one observable outcome.
- If you show an Event Modeling board, time must flow left to right on a horizontal timeline.
- Use lanes only as semantic layers; do not draw Event Modeling as a top-to-bottom flowchart.
- Put views, automations, and translations near the event they depend on.
- If the board is illustrative rather than exhaustive, say so explicitly.
