---
name: crablet-app-dev
description: >
  Use this skill for application work in this Crablet app: adding feature slices,
  sequencing event-model.yaml with codegen, using embabel_plan or embabel_generate,
  implementing app command handlers, views, automations, outbox publishers, and
  verifying generated app code. Do not use for spring-crablet framework module
  internals or public API changes.
---

# Crablet App Development

This skill is for developers building this Crablet application.

Source of truth: this template skill mirrors the framework repo skill at
`.claude/skills/crablet-app-dev/SKILL.md`. Template-only wording says that this
repo is the generated app root.

## Routing

- Use `event-modeling` for workshop dialogue and generator-ready `event-model.yaml` shape.
- Use `crablet-greenfield` for end-to-end greenfield pacing across app baseline, modeling, slices, and app evolution.
- Use this skill to sequence the app workflow around that model and implement/repair app code.
- Use `crablet-dcb` for deep DCB diagnosis, `ConcurrencyException` analysis, or command-pattern explanation when available.
- Framework module, public API, template, or codegen internals belong in the spring-crablet repo, not this app.

## Feature Slice Workflow

Work one vertical slice at a time, scoped to one observable user outcome.

1. Ask for missing business facts before changing files.
2. Use or sequence with `event-modeling` to update `event-model.yaml` first.
3. After any model-affecting change, run `make diagram-preview` from this app root or provide a
   textual board walk-through; check actors, lanes, commands, events, views, automations, and outbox
   before planning.
4. Run `embabel_plan` and show the planned artifacts.
5. Ask for confirmation before `embabel_generate`.
6. Call `embabel_generate` with `output` set to `src/main/java`.
7. Run `./mvnw verify` after generation or manual repair.
8. Prefer improving `event-model.yaml` over hand-patching generated structural code.

For Claude Code and Cursor, use MCP tools when available. For Codex, other agents, or terminal
workflows, use `make plan`, `make generate`, and `make verify` from the app root. `plan` is
deterministic and does not call a model; `generate` uses the configured codegen provider.

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

## Generated Code Boundaries

- Generated command-handler artifacts are structural interfaces. User logic belongs in separate `@Component` implementation classes.
- Generated automation handler and outbox publisher artifacts should carry metadata only; application code implements behavior in separate components.
- Client setup, credentials, retry policy, and adapter-specific external integration code belong in application-owned boundaries.
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

- If MCP is unavailable, prefer the Makefile targets over hand-running long `java -jar` commands.
- Do not generate code until the artifact plan has been reviewed.
- Do not skip `event-model.yaml`; it is the structural source for generated app code.
- Do not put external HTTP/webhook publishing inside automations; use outbox for reliable publication.
- LISTEN/NOTIFY wakeup requires a direct Postgres JDBC URL, not PgBouncer transaction mode, PgCat, or RDS Proxy.
