---
name: event-modeling
description: >
  Use this skill when modelling or revising domain behaviour for this Crablet app—events,
  commands in event-model.yaml, views, automations, and outbox—with Event Modeling facilitation
  techniques. Prefer /dcb for AppendCondition and consistency-boundary specifics; prefer
  /crablet-app-dev for plan → generate → verify sequencing once the YAML is drafted.
---

# Event Modeling

Use this skill when the user wants to add or change a Crablet feature slice.

## Facilitation Aids

Use these when starting a new slice or unblocking a stuck session.

**Brain dump mode** — Ask the user to list everything that happened in the domain without
worrying about naming or ordering. Extract named events afterward.

**Timeline arrangement** — Arrange events left to right by rough occurrence order. Ask: "Which
of these must happen before which?" Let ordering emerge from dependencies.

**Hot spots** — Ask: "Where is there contention or ambiguity?" Mark those areas before designing
commands or views; they often reveal missing events or contested consistency boundaries.

**Feature slice summary (Given/When/Then)** — Summarize each slice:
- *Given* — the prior events (the current state)
- *When* — the command is submitted
- *Then* — the expected events and resulting read-model values

**Saga-like workflow modeling** — For multi-step processes, model explicitly:
1. A committed event wakes an automation.
2. A TODO/read model holds accumulated decision state.
3. The automation reads that state and emits a command or returns NoOp.
4. The emitted command records the next event.
5. External publication uses outbox.
6. Idempotent downstream commands protect at-least-once retries.

Do not use hidden generic saga state. Make decision state explicit as a TODO/read model.

**Policies in Crablet** — Event Storming uses "policies" (when X happens, do Y) as a single
concept. Crablet uses an Event Modeling approach instead: TODO/read models hold decision state,
automations read those views when the system should react, and commands record the next fact. The
right construct depends on who or what reacts:

| Policy type | Trigger | Crablet model |
|---|---|---|
| Automated reaction | Event → system emits command | `automations:` → generated `AutomationHandler` |
| Stateful automated reaction | Event → check accumulated state → emit command | `views:` (TODO/work queue) + `automations:` |
| Human decision | Event → person reviews and acts | `views:` (work queue) + `commands:` |
| External publication | Event → publish outside Crablet | `outbox:` → generated `OutboxPublisher` |

Ask for each reaction: "Does a person need to act, or does the system act automatically? Does
it publish outside Crablet, or emit another command inside it?"

Do not model human decisions as automations. The view IS the human policy's inbox.

## Workflow

1. Ask for the user-visible outcome.
2. Keep the scope to one vertical slice.
3. Identify commands, events, tags, validations, read models, automations, and outbox publishers.
4. Ask clarifying questions before editing `event-model.yaml`.
5. Update `event-model.yaml` only after the missing facts are known.
6. Run `embabel_plan` and show the planned artifacts.
7. Ask for confirmation before running `embabel_generate`.
8. Run `./mvnw verify` after generation.

For Claude Code and Cursor, use MCP tools when available. For Codex, other agents, or terminal
workflows, use `make plan`, `make generate`, and `make verify`. `plan` is deterministic and does
not call a model; `generate` uses the configured codegen provider.

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

For each reaction or policy:

- Which event triggers it?
- Is it automated (system acts) or human (person reviews and decides)?
- **Automated reaction**: which command does the system emit?
- **Stateful automated reaction**: which TODO/read `views:` entry does it read before deciding?
- **Human decision**: which `views:` work queue surfaces the pending item, and which `commands:` entry does the person submit?
- **External publication**: which `outbox:` topic and adapter boundary owns the publication?

## Rules

- Do not invent credentials, endpoints, or adapter details.
- Do not generate code before reviewing the artifact plan.
- Prefer improving `event-model.yaml` over hand-editing generated structural code.
- Keep generated slices small enough to verify with one command and one observable outcome.
- If you show an Event Modeling board, time must flow left to right on a horizontal timeline.
- Swim lanes divide the board by sub-system (e.g. inventory, auth, payment) — not by element type.
- Within each lane, element types are stacked vertically: wireframes, commands, events, read models.
- Slices cut vertically through all element layers — one slice per feature (State Change, State View, Automation, Translation).
- Do not draw Event Modeling as a top-to-bottom flowchart.
- If the board is illustrative rather than exhaustive, say so explicitly.

Downstream sequencing lives in `crablet-app-dev`; full greenfield pacing lives in
`crablet-greenfield`.
