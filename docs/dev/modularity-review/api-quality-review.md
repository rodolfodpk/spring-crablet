# API Quality Review — Current Status

**Scope**: public `EventStore` API, command handler API, `CommandDecision`, `CommandExecutor`,
and related value types.

**Status date**: May 2026

## Executive Summary

The API review originally identified several pre-1.0 polish issues around abstraction boundaries,
typed command decisions, and direct event-store ergonomics. The critical boundary issues have
since been fixed. The remaining items are accepted tradeoffs: `executeInTransaction(...)` remains
public for advanced direct `EventStore` usage, and `appendIdempotent(...)` keeps both a simple
raw-string overload and a `Query` overload.

The current API shape is coherent enough to treat as the public baseline, with focused guardrail
tests to prevent the old issues from reappearing.

## Status Table

| Finding | Current status | Notes |
|---|---|---|
| `storeCommand(...)` exposed on `EventStore` | Fixed | Command audit now lives on `CommandAuditStore`, not `EventStore`. `EventStoreImpl` can implement both without exposing command audit to views, outbox, automations, or direct event-store users. |
| `hasConflict(...)` exposed on `EventStore` | Fixed | Guard checks now use projection/existence primitives instead of a command-layer method on the foundational interface. |
| `CommutativeCommandHandler.decide(...)` returned parent `CommandDecision` | Fixed | It now returns `CommandDecision.CommutativeDecision`, preventing commutative handlers from returning non-commutative or idempotent decisions. |
| `CommutativeGuarded` and `NonCommutative` looked structurally interchangeable | Fixed | `CommutativeGuarded.withLifecycleGuard(...)` names the intended semantics, and its constructor rejects guard queries that include appended event types. |
| `ProjectionResult` carried a write helper | Fixed | `ProjectionResult` is now a passive value object with `state`, `streamPosition`, and static factories only. Direct callers pass `projection.streamPosition()` to append methods explicitly. |
| `Query.empty()` had dual semantics | Fixed | `Query.empty()` documents match-all read semantics. `Query.noCondition()` documents skipped condition checks for append conditions. |
| `appendIdempotent(...)` raw strings vs `Query` overload | Accepted tradeoff | The raw-string overload is the simple common case; the `Query` overload is for advanced or multi-criteria idempotency checks. |
| `executeInTransaction(...)` on `EventStore` | Accepted tradeoff | It is the public advanced primitive for direct `EventStore` users who need project/exists/append/audit composition on one transaction-scoped store. Ordinary application writes should use `CommandExecutor`. |

## Current API Boundary

`EventStore` is the foundational application-facing abstraction for event appends, projections,
existence checks, and direct transaction composition. It should not expose command-layer concerns.
The public guardrail is simple: methods such as `storeCommand(...)` and `hasConflict(...)` must
not appear on `EventStore`.

`CommandExecutor` owns command execution. It runs handler decision logic, dispatches the resulting
`CommandDecision`, handles idempotent results, records command audit when enabled, and reports
metrics. Its public `execute(...)` overloads return `ExecutionResult`.

`CommandDecision` encodes concurrency intent:

- `Commutative` for order-independent appends.
- `CommutativeGuarded` for order-independent appends with a lifecycle guard.
- `NonCommutative` for DCB stream-position checks.
- `Idempotent` for duplicate prevention or idempotent replay handling.
- `NoOp` for handlers that detect work has already been applied.

## Accepted Tradeoffs

### `executeInTransaction(...)`

Keeping `EventStore.executeInTransaction(...)` public broadens `EventStore` beyond simple event
storage, but the tradeoff is useful. It gives direct `EventStore` users the same primitive the
command layer relies on: one JDBC connection, configured isolation, atomic commit/rollback, and a
transaction-scoped store for projections and appends.

Command audit remains outside `EventStore`. When the transaction-scoped store also implements
`CommandAuditStore`, command execution can store audit metadata in the same transaction without
putting audit methods on the `EventStore` interface.

### `appendIdempotent(...)` overloads

The raw-string overload:

```java
appendIdempotent(events, eventType, tagKey, tagValue)
```

is intentionally kept as the common-case API for one event type plus one tag. The `Query` overload:

```java
appendIdempotent(events, idempotencyQuery)
```

supports advanced idempotency criteria without forcing simple callers to build a `Query`.

## Guardrail Tests

The API baseline should be protected by focused tests rather than a brittle snapshot of every
public method:

- `EventStore` does not expose `storeCommand` or `hasConflict`.
- `CommandExecutor.execute(...)` overloads return `ExecutionResult`.
- `CommutativeCommandHandler.decide(...)` returns `CommandDecision.CommutativeDecision`.
- `ProjectionResult` does not grow write-path methods or parameters.

These tests are intentionally narrow: they protect the architectural decisions from this review
without blocking unrelated public helpers.
