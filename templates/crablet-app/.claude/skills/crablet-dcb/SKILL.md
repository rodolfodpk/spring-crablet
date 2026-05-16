---
name: crablet-dcb
description: >
  Use this skill in this Crablet application for Dynamic Consistency Boundary (DCB) patterns:
  idempotent vs commutative vs non-commutative commands, tags as the consistency boundary,
  guardEvents lifecycle preconditions, and diagnosing ConcurrencyException. For modeling
  workshop flow and event-model.yaml structure, see /event-modeling; for sequencing plan,
  generate, and verify around a slice, see /crablet-app-dev.
---

# Crablet DCB (Dynamic Consistency Boundary)

DCB (“Killing the Aggregate”) defines a **decision model** (which events matter) plus **tags**
that carve the consistency boundary—not a locked aggregate stream. Official overview:
https://dcb.events/

In **event-model.yaml**, `pattern` selects the coarse strategy (`idempotent` |
`commutative` | `non-commutative`); codegen maps that to AppendCondition semantics. **`tags`**
on events and commands scope who races with whom. **`guardEvents`** declares lifecycle facts
required before append (orthogonal to commutative vs cursor-based concurrency).

Routing: workshop and YAML shape → `/event-modeling`. Slice sequencing and codegen →
`/crablet-app-dev`.

## Pattern decision (three cases)

Declares **idempotent**, **commutative**, or **non-commutative** in the YAML (see
`/event-modeling` for field meanings).

| YAML pattern | Typical use | Boundary / concurrency idea |
|---|---|---|
| **idempotent** | Create unique entity / first-time facts | Duplicate submit must not duplicate effect; codegen uses idempotency-style checks |
| **commutative** | Order-insensitive totals (e.g. deposits / credits) when rules allow pure append | Highest throughput where business rules tolerate no ordering guard |
| **non-commutative** | Withdraw, transfer caps, approvals—state-dependent | Cursor-style “anything in my boundary after read?” detects conflicts → retry at **application** layer |

**guardEvents:** Use when append order can be arbitrary but the entity (or precondition) **must**
exist—e.g. `guardEvents: [WalletOpened]` on wallet commands. This is lifecycle, not saga
orchestration.

## Tags define the consistency boundary

Smaller scopes mean less contention. Examples:

- One wallet → tag events with that wallet key; other wallets proceed independently.
- Transfer affecting two wallets → one event can carry tags for both sides so the decision
  model includes every fact that can invalidate the decision.

Tags on **projected / decision-model** queries must match tags on stored events exactly, or you
will see wrong conflicts or missed conflicts.

## When DCB avoids hidden “sagas”

DCB replaces **locking a single aggregate** with scoped queries—it does **not** replace **long
running collaboration**. For visible multi-step workflows, prefer explicit **TODO read models**
(or automations polling them) driven by facts in the journal, rather than opaque saga state:

1. Fact committed → automation or poll sees work in a view row.
2. Next command emits the next fact; **retry-safe** handlers absorb at-least-once delivery.

Model that flow in `/event-modeling`; implement handler idempotency and patterns here.

## Automation and at-least-once retries

Poller-backed flows run **at least once**. **`idempotent`** commands are the usual way emitted
automations tolerate duplicate wakes. **`commutative`** commands skip ordering conflicts when the
business allows it. **`non-commutative`** absorbs races via `ConcurrencyException` and **outer**
retries—not retries inside handler transactions.

## Diagnosing ConcurrencyException vs idempotency

Read the **`Hint:`** in the violation message—**duplicate operation** suggests idempotency tag
overlap; **conflict after read** suggests non-commutative races. Enable
`logging.level.com.crablet.eventstore=DEBUG` for DCB check parameters when needed.

## Where the Java lives

Your app’s generated `CommandHandler` implementations call into the framework’s append path
(`CommandExecutor` / `appendIf`). Do not call `appendIf` yourself from handlers; return
events and append conditions per the generator’s interface. Framework reference code and deeper
internals live in the spring-crablet repo Maintainer skill set.
