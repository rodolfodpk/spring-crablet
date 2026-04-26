# API Quality Review — EventStore and CommandHandler

**Scope**: `EventStore` interface (`crablet-eventstore`) and CommandHandler API surface (`crablet-commands`): `CommandDecision` sealed type, `CommandHandler` sub-interfaces, `CommandExecutor`
**Date**: April 2026

## Executive Summary

Spring-Crablet is a lightweight Java 25 event-sourcing framework built on the DCB (Dynamic Consistency Boundary) pattern. The framework's core APIs are well-conceived: the `CommandDecision` sealed type cleanly encodes concurrency intent, and the typed command-handler sub-interfaces give users a clear contract to implement. The overall modularity is healthy, with one critical issue and two significant issues that should be addressed before the API stabilizes. The most important finding is that `EventStore`'s public interface has been shaped by `CommandExecutorImpl`'s internal needs, introducing [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) from a higher abstraction layer back into the foundational module — the opposite of the intended dependency direction.

## Coupling Overview

| Integration | [Strength](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | [Distance](https://coupling.dev/posts/dimensions-of-coupling/distance/) | [Volatility](https://coupling.dev/posts/dimensions-of-coupling/volatility/) | [Balanced?](https://coupling.dev/posts/core-concepts/balance/) |
|---|---|---|---|---|
| `CommandExecutorImpl` → `EventStore` (core usage) | [Model](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | High | ✅ Yes |
| `EventStore` API ← `CommandExecutorImpl` internals (`storeCommand`, `hasConflict`) | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | High (different abstraction layers) | High | ❌ No |
| `CommutativeGuarded` ↔ `NonCommutative` (identical structure, different semantics) | [Model](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | High | ❌ No |
| `CommutativeCommandHandler.decide()` → `CommandDecision` (widened return type) | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | ❌ No |
| `appendIdempotent` raw strings vs `appendNonCommutative` typed `Query` | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low | ✅ Tolerable |
| `ProjectionResult` → `EventStore` (write method on read result) | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low | ✅ Tolerable |
| `Query.empty()` dual semantics | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low | ✅ Tolerable |

---

## Issue: Command-layer internals bleeding into EventStore interface

**Integration**: `CommandExecutorImpl` (`crablet-commands`) → `EventStore` (`crablet-eventstore`) — reversed knowledge flow
**Severity**: Critical

### Knowledge Leakage

`EventStore` exposes two methods that exist solely to serve `CommandExecutorImpl`'s internal needs:

- `storeCommand(commandJson, commandType, transactionId)` — writes a command audit trail as the final step of a transaction. The command executor calls this; nothing in the event-store layer needs it. The Javadoc does not explain why command auditing belongs in a foundational event-storage interface.
- `hasConflict(Query, StreamPosition)` — the Javadoc explicitly states _"Used by `CommandExecutorImpl` to enforce a selective DCB guard on commutative operations."_ That is a private implementation detail of one caller, published in the contract of the thing it calls.

Neither concern belongs to event storage. Storing commands is a command-execution concern. Checking for lifecycle conflicts is a concurrency strategy of the command layer. Both are leaking [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) from `crablet-commands` backwards into `crablet-eventstore`.

### Complexity Impact

`crablet-eventstore` is designed to be the foundational module, usable independently of `crablet-commands` — for views, outbox, automations, or standalone event queries. Yet every consumer of `EventStore` (views, outbox, test implementations, future integrations) is now exposed to `storeCommand` and `hasConflict`, two methods they will never call.

This violates the principle of [modularity](https://coupling.dev/posts/core-concepts/modularity/) that a module's interface should reflect only its own responsibilities. The cognitive overhead is real: a developer approaching `EventStore` for the first time sees 11 methods and must reason about why an event store stores commands and checks concurrency guards.

### Cascading Changes

If the `CommutativeGuarded` guard mechanism evolves — for example, if it needs to check a different kind of conflict, accumulate results, or be made atomic at the database level — the change touches `CommandExecutorImpl` (which orchestrates the guard) **and** `EventStore` (whose `hasConflict` signature may need to change) **and** `EventStoreImpl` (which implements it). A change that is logically confined to `crablet-commands` cascades into the foundational interface.

Similarly, if command auditing grows to store additional metadata (correlation IDs, causation chains), `storeCommand`'s signature widens — a change forced onto every `EventStore` implementor, including `InMemoryEventStore` in `crablet-test-support`.

### Recommended Improvement

**For `storeCommand`**: Move to a separate `CommandAuditStore` interface in `crablet-commands`, co-located with the command execution pipeline that needs it. `EventStoreImpl` can implement it via a package-accessible path without exposing it on the public `EventStore` contract.

**For `hasConflict`**: Remove from `EventStore`. The default implementation already delegates to `project()`:

```java
default boolean hasConflict(Query query, StreamPosition after) {
    return project(query, after, StateProjector.exists()).state();
}
```

`CommandExecutorImpl` can call `project()` directly. The guard logic then lives entirely within `crablet-commands` where it belongs, and `EventStore` loses nothing — it already provides all the primitives needed.

The trade-off: `CommandExecutorImpl` gains a slightly more verbose guard check. The benefit: `EventStore`'s contract no longer couples to command-layer implementation details, and implementors of `EventStore` (including `InMemoryEventStore`) no longer carry a method imposed by a module they know nothing about.

---

## Issue: `CommutativeGuarded` and `NonCommutative` are structurally identical

**Integration**: `CommandExecutorImpl` → `CommandDecision.CommutativeGuarded` / `CommandDecision.NonCommutative`
**Severity**: Significant

### Knowledge Leakage

Both variants are records with exactly the same structure:

```java
record CommutativeGuarded(List<AppendEvent> events, Query guardQuery,  StreamPosition guardPosition)
record NonCommutative    (List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition)
```

Their factory methods have identical call signatures:

```java
CommutativeGuarded.of(event, guardQuery, position)
NonCommutative.of(event, decisionModel, position)
```

The only distinction is what `CommandExecutorImpl` does with them: `NonCommutative` triggers a full DCB conflict check on all events matching the decision model; `CommutativeGuarded` checks only lifecycle events and then calls `appendCommutative`. These are fundamentally different concurrency semantics, but the type system communicates nothing of that difference. The only hint is the parameter name (`guardQuery` vs `decisionModel`) — which neither the compiler nor the IDE can enforce.

### Complexity Impact

Choosing the wrong variant is a silent correctness bug. A developer who mistakenly returns `NonCommutative` from a `CommutativeCommandHandler` will serialize deposits that should be parallel. A developer who mistakenly returns `CommutativeGuarded` from a `NonCommutativeCommandHandler` will allow concurrent withdrawals that should be blocked. Neither mistake produces a compile error or even a clear runtime failure — the application just behaves incorrectly under concurrency.

This is a classic case where [model coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) without semantic differentiation leaves a correctness invariant expressed only in documentation, not in code.

### Cascading Changes

If the guard semantics of `CommutativeGuarded` are refined (e.g., the guard needs to express "lifecycle only — no deposit types") and the enforcement moves from a runtime convention to a validated constraint, both the type and `CommandExecutorImpl`'s handling of it must change in sync. Nothing in the current structure makes it easy to discover all the places that depend on the implicit "guard query must be lifecycle-only" rule.

### Recommended Improvement

Make the semantic distinction visible at the construction site. Two complementary options:

**Option A — rename the factory to encode intent**:

```java
// Instead of:
CommutativeGuarded.of(event, guardQuery, position)
// Use:
CommutativeGuarded.withLifecycleGuard(event, lifecycleOnlyQuery, position)
```

The factory name makes it explicit that the `Query` parameter must contain only lifecycle event types, not the commutative type being appended.

**Option B — validate the constraint at construction**:

In the `CommutativeGuarded` compact constructor, verify that none of the guard-query event types appear in the events being appended. This catches the most common mistake (including `DepositMade` in the guard query) at the earliest possible moment with a clear error message.

Both options are low-cost and can coexist. Option A improves readability immediately; Option B adds a runtime safety net.

---

## Issue: `CommutativeCommandHandler.decide()` returns the parent sealed type

**Integration**: `CommutativeCommandHandler<C>` → `CommandDecision` variants
**Severity**: Significant

### Knowledge Leakage

The three typed sub-interfaces establish a consistent pattern: each narrows the return type of `decide()` to the specific `CommandDecision` variant it is expected to return.

| Sub-interface | `decide()` return type |
|---|---|
| `NonCommutativeCommandHandler<C>` | `CommandDecision.NonCommutative` |
| `IdempotentCommandHandler<C>` | `CommandDecision.Idempotent` |
| `CommutativeCommandHandler<C>` | `CommandDecision` ← parent sealed type |

`CommutativeCommandHandler` breaks this pattern. It returns the parent `CommandDecision` because it is designed to support two variants — `Commutative` and `CommutativeGuarded` — and Java does not allow a covariant return type that unions two sibling types. The legitimate intent is correct; the consequence is that the [contract coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) is weakened: a `CommutativeCommandHandler` implementor could accidentally return `NonCommutative` or `Idempotent` and the compiler would not object.

The `CommutativeCommandHandler` Javadoc compensates by listing the valid return types in prose. This is knowledge that belongs in types.

### Complexity Impact

The inconsistency creates two levels of cognitive load. First, a developer implementing `CommutativeCommandHandler` cannot rely on the return type to guide them — they must read the Javadoc to discover the valid options. Second, a developer reading an existing `CommutativeCommandHandler` implementation cannot verify at a glance that the return type is correct — they must pattern-match manually against the Javadoc contract.

This gap will widen as more command patterns are introduced. If a new variant is added to `CommandDecision`, the compiler will not warn that `CommutativeCommandHandler` implementations have not been updated to consider it.

### Cascading Changes

If a new commutative variant is introduced, `CommandExecutorImpl`'s switch must be updated, but `CommutativeCommandHandler`'s contract gives no signal. In contrast, if a new `NonCommutativeDecision` variant were needed, the typed return would force consideration at every implementation site.

### Recommended Improvement

Introduce a sealed intermediate type that the two commutative variants can implement:

```java
sealed interface CommutativeDecision extends CommandDecision
    permits CommandDecision.Commutative, CommandDecision.CommutativeGuarded {}
```

Then:

```java
public interface CommutativeCommandHandler<C> extends CommandHandler<C> {
    CommutativeDecision decide(EventStore eventStore, C command);
}
```

`CommandExecutorImpl` pattern-matches on `CommutativeDecision` as the parent case, then dispatches within it. The change restores the compile-time guarantee for commutative implementors and makes `CommutativeCommandHandler` consistent with its siblings.

The only trade-off is a small structural change to `CommandDecision`'s sealed hierarchy. The benefit is that the entire pattern becomes uniform and machine-verifiable.

---

## Issue: `appendIdempotent` uses raw strings where `appendNonCommutative` uses a typed `Query`

**Integration**: `EventStore.appendIdempotent` ↔ `EventStore.appendNonCommutative` (same interface, different abstraction levels)
**Severity**: Minor

### Knowledge Leakage

The three append methods on `EventStore` operate at inconsistent abstraction levels:

```java
appendCommutative(List<AppendEvent> events)
appendNonCommutative(List<AppendEvent> events, Query decisionModel, StreamPosition after)
appendIdempotent(List<AppendEvent> events, String eventType, String tagKey, String tagValue)
```

`appendNonCommutative` receives a typed `Query` — the same abstraction used for projection throughout the API. `appendIdempotent` receives three raw `String` parameters that together represent a degenerate single-item query. Callers can use `EventType.type(MyEvent.class)` for the first string, but there is no typed counterpart for the tag key (which in practice comes from a constants class like `WalletTags.WALLET_ID`).

The asymmetry is low-volatility — idempotency checks on multiple event types or multiple tags are rare — but it adds unnecessary cognitive friction when reading the interface.

### Complexity Impact

A developer familiar with `appendNonCommutative` and `Query` will be surprised by the flat-string signature of `appendIdempotent`. The inconsistency signals that these methods were designed at different points in time, which invites the question: _is there a deeper reason for the difference?_ There isn't — it is accidental.

### Cascading Changes

If idempotency checks ever need to span multiple tags or event types, a new method overload would be required. With a typed `Query`, that extension is already handled by the existing abstraction.

### Recommended Improvement

Add a `Query`-accepting overload alongside the existing raw-string signature:

```java
String appendIdempotent(List<AppendEvent> events, Query idempotencyQuery);
```

Keep the existing raw-string method as a convenience overload (or deprecate it over time). `AppendCondition.idempotent()` already builds the equivalent `Query` internally, showing the pattern is viable.

---

## Issue: `CommandExecutor.execute()` returns `void` while `executeCommand()` returns `ExecutionResult`

**Integration**: `CommandExecutor.execute` ↔ `CommandExecutor.executeCommand`
**Severity**: Minor

### Knowledge Leakage

`CommandExecutor` exposes two methods for the same logical operation:

```java
<T> ExecutionResult executeCommand(T command);
<T> void execute(T command, CommandHandler<T> handler);
```

The method that auto-discovers the handler returns an `ExecutionResult` (carrying idempotency information). The method that accepts an explicit handler returns `void`, silently discarding the result. The naming is also inconsistent: `executeCommand` versus `execute` for the same concept.

### Complexity Impact

Callers using the explicit-handler path (`execute`) lose the ability to distinguish a new write from an idempotent replay — information needed to emit the correct HTTP status code (`201 Created` vs `200 OK`) or record the correct metric. The loss is invisible: the method compiles and runs, but the caller is left with less information than they would have had using the auto-discovery path.

### Cascading Changes

This inconsistency is low-volatility. It is unlikely to cause cascading changes, but it does create an invisible trap for callers who switch between the two methods expecting equivalent behavior.

### Recommended Improvement

Align the return types:

```java
<T> ExecutionResult execute(T command);
<T> ExecutionResult execute(T command, CommandHandler<T> handler);
```

Use overloads of the same method name (`execute`) rather than two distinct names, consistent with standard Java API conventions.

---

## Issue: `ProjectionResult` carries a write side-effect method

> Update (April 2026): `ProjectionResult.appendNonCommutative(...)` has since been removed from the public API. Direct `EventStore` callers now pass `projection.streamPosition()` explicitly to `EventStore.appendNonCommutative(...)`.

**Integration**: `ProjectionResult<T>` → `EventStore` (write path)
**Severity**: Minor

### Knowledge Leakage

`ProjectionResult<T>` is a query result value carrying projected state and a stream position. It also exposes:

```java
public String appendNonCommutative(EventStore eventStore, List<AppendEvent> events, Query decisionModel)
```

The rationale — stated clearly in the Javadoc — is ergonomic: embedding the stream position in the result prevents callers from accidentally discarding or misusing it. This is a deliberate trade-off, not an oversight.

The cost is that a read result type can trigger a write. Any developer reading `ProjectionResult` must understand that it is both a state carrier and an action performer. This blurs the [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) boundary between the query and command paths.

### Complexity Impact

The impact is limited because the method is narrow in scope and low-volatility. However, the existence of `CommandDecision` patterns means that most command handlers today never call `ProjectionResult.appendNonCommutative` directly — they return a `CommandDecision` instead, and the executor handles the append. The method's primary audience is now reduced to callers working with `EventStore` outside the command framework.

### Recommended Improvement

Accept the trade-off as intentional, but reinforce the distinction in the class Javadoc: this method is provided for direct-`EventStore` usage patterns only; within the command framework, return `CommandDecision.NonCommutative` instead. Consider adding a `@deprecated` notice if the method's usage has been fully subsumed by `CommandDecision` to signal that the direct-append path is no longer the recommended one.

---

## Issue: `Query.empty()` serves two semantically opposite roles

**Integration**: `Query.empty()` used in both `EventStore.exists()` (match-all) and `AppendCondition` (skip-check)
**Severity**: Minor

### Knowledge Leakage

`Query.empty()` is used in two distinct roles across the codebase:

1. **Match-all filter**: in `exists()` and `StateProjector.exists()`, an empty item list means no type filter is applied, so all events from the database query are considered.
2. **No-check sentinel**: in `AppendCondition.empty()` and idempotency check paths, an empty `Query` signals that the check should be skipped entirely.

These are semantically opposite — one means _"everything qualifies"_, the other means _"nothing is checked"_. The value happens to work in both roles because the implementation handles both cases correctly, but a developer who encounters `Query.empty()` in an unfamiliar context must trace through the implementation to understand which role it plays.

### Complexity Impact

The ambiguity is low-volatility and unlikely to cause runtime bugs. The risk is misuse by future contributors who see `Query.empty()` used in one context and apply it in the other without realising the semantic difference. It also makes code reviews harder: `appendCondition.empty()` reads as "no events match" when it actually means "no check is performed".

### Recommended Improvement

Introduce `Query.noCondition()` (or `Query.unchecked()`) as an explicit factory for the skip-check role, and reserve `Query.empty()` exclusively for the match-all filter role. The implementation can return the same underlying value — this is a naming change for clarity, not a structural one. Code paths that currently use `Query.empty()` as a no-check sentinel are updated to `Query.noCondition()`, making intent self-documenting.

---

## Summary

| # | Issue | Severity | Area |
|---|-------|----------|------|
| 1 | `storeCommand` + `hasConflict` on `EventStore` | Critical | `EventStore` |
| 2 | `CommutativeGuarded` / `NonCommutative` structurally identical | Significant | `CommandDecision` |
| 3 | `CommutativeCommandHandler.decide()` returns parent sealed type | Significant | `CommandHandler` |
| 4 | `appendIdempotent` raw strings vs typed `Query` | Minor | `EventStore` |
| 5 | `execute()` returns `void`; `executeCommand()` returns `ExecutionResult` | Minor | `CommandExecutor` |
| 6 | `ProjectionResult.appendNonCommutative()` — side-effect on a read result | Minor | `EventStore` / `Query` |
| 7 | `Query.empty()` dual semantics | Minor | `EventStore` |

---

_This analysis was performed using the [Balanced Coupling](https://coupling.dev) model by [Vlad Khononov](https://vladikk.com)._
