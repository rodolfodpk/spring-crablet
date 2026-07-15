---
name: crablet-app-dev
description: >
  Use this skill for application work in this Crablet app: adding feature slices,
  implementing command handlers, views, automations, and outbox publishers by
  hand, and verifying app code. Do not use for spring-crablet framework module
  internals or public API changes.
---

# Crablet App Development

This skill is for developers building this Crablet application, writing handlers, views,
automations, and outbox publishers directly in Java.

Source of truth: this template skill mirrors the framework repo skill at
`.claude/skills/crablet-app-dev/SKILL.md`. Template-only wording says that this
repo is the generated app root.

An AI-assisted codegen path (`event-model.yaml` → generated structural code) exists as a
separate, pré-1.0/experimental track — see `crablet-codegen`, `crablet-event-modeling`, and the
spring-crablet `docs/dev/PRODUCT_ROADMAP.md`. It is not required; this skill covers manual
Java-first development, which is the default and recommended path today.

## Routing

- Use this skill to sequence and implement app command handlers, views, automations, and
  outbox publishers by hand.
- Use `crablet-greenfield` for end-to-end pacing across app baseline, first slice, and app evolution.
- Use `crablet-dcb` for deep DCB diagnosis, `ConcurrencyException` analysis, or command-pattern explanation when available.
- Framework module, public API, template, or codegen internals belong in the spring-crablet repo, not this app.

## Feature Slice Workflow

Work one vertical slice at a time, scoped to one observable user outcome.

1. Ask for missing business facts before changing files.
2. Write the command record, its validation, and the command handler.
3. Write the event record(s) the handler appends, with tags matching the consistency boundary.
4. Write the state projector / view needed to observe the outcome, if any.
5. Write the automation or outbox publisher, if the slice needs a reaction or external effect.
6. Write handler/view/automation tests.
7. Run `./mvnw verify` after each change.

For each slice, clarify:

- command name, fields, validation, and DCB pattern
- event name, fields, and tags
- consistency checks and guard events
- read model needed to observe the outcome
- automation or outbox behavior, if needed
- sample scenario used to verify the slice

## DCB Choice For App Commands

Use this as a quick choice guide.

| Pattern | Use when | App result |
|---------|----------|------------|
| `idempotent` | Creating a unique entity or operation record | duplicate submit returns the existing effect |
| `commutative` | Order does not affect the final outcome | no lifecycle guard is required |
| `commutative` with guard events | Order does not matter, but the entity must exist or be active | lifecycle query guards the append |
| `non-commutative` | Current state affects validity | projection stream position detects conflicts |

Tags define the consistency boundary. Ensure decision-model tags match the tags on appended events.

## Code Boundaries

- Command handlers hold decision logic; keep external clients, credentials, and retry policy in
  application-owned adapters, not in the handler.
- Automation handlers and outbox publishers should stay focused on their single reaction/publish
  responsibility; put shared decision state in an explicit read model, not hidden handler state.
- Commands validate at construction. Handlers may assume command values are valid.
- Use `ClockProvider.now()` instead of `Instant.now()` for deterministic tests.

## Runtime Modules

- `crablet-eventstore`: required event storage, queries, projections, DCB appends.
- `crablet-commands`: command handlers and `CommandExecutor`.
- `crablet-commands-web`: optional generic HTTP command API.
- `crablet-views`: materialized read models.
- `crablet-automations`: event-driven commands/reactions.
- `crablet-outbox`: reliable external publication.
- `crablet-event-poller`: shared polling infrastructure behind views, automations, and outbox.
- `crablet-metrics-micrometer`: optional metrics.

Poller-backed modules process at least once. Make views and publishers idempotent.

## App Testing

- Run `./mvnw verify`.
- Command handlers: unit test decisions with representative prior events.
- Views: test idempotent upserts and all event variants listed by the projector.
- Automations: test trigger event selection, condition behavior, and emitted command mapping.
- Outbox: test publisher behavior at the adapter boundary without inventing real credentials.

## Process Rule

For any multi-step event-driven workflow:

1. A committed event wakes an automation.
2. A TODO/read model holds accumulated decision state.
3. The automation reads that state and returns `ExecuteCommand` or `NoOp`.
4. The emitted command records the next event.
5. External publication uses outbox.
6. Idempotent downstream commands protect at-least-once retries.

Do not put hidden generic saga state inside automation handlers. If an automation needs shared
decision state across events, model it as an explicit TODO/read model that the automation reads.

## App Gotchas

- Do not put external HTTP/webhook publishing inside automations; use outbox for reliable publication.
- LISTEN/NOTIFY wakeup requires a direct Postgres JDBC URL, not PgBouncer transaction mode, PgCat, or RDS Proxy.

**Status:** the manual Java-first workflow above is the stable, recommended path. The
AI-assisted codegen alternative is pré-1.0/experimental — see the spring-crablet
`docs/dev/PRODUCT_ROADMAP.md`.
