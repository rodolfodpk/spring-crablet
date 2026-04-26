# Modularity Review

**Scope**: Framework modules — `crablet-eventstore`, `crablet-commands`, `crablet-event-poller`, `crablet-views`, `crablet-outbox`, `crablet-automations`
**Date**: 2026-04-06

## Executive Summary

Spring-Crablet is a Java 25 event sourcing framework built on the DCB (Dynamic Consistency Boundary) pattern, providing event storage, command execution, read model projections, transactional outbox, and event-driven automations as a suite of optional Maven modules. The overall module structure is well-conceived: a clean layering of core and optional capabilities, with appropriate dependency direction. However, three [coupling](https://coupling.dev/posts/core-concepts/coupling/) issues were identified, one of which is critical. The most important finding is that a concrete domain command name (`"open_wallet"`) is hardcoded inside the generic `crablet-commands` framework module — a breach of the boundary between infrastructure and domain that will compound as the framework and its example domains continue to evolve under active development.

## Coupling Overview

| Integration | [Strength](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | [Distance](https://coupling.dev/posts/dimensions-of-coupling/distance/) | [Volatility](https://coupling.dev/posts/dimensions-of-coupling/volatility/) | [Balanced?](https://coupling.dev/posts/core-concepts/balance/) |
| --- | --- | --- | --- | --- |
| `commands` → `eventstore` (EventStore API) | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | ✓ Yes — high cohesion, low distance |
| `CommandExecutorImpl` ← `"open_wallet"` domain logic | [Intrusive](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | High | ✗ **No** — domain knowledge embedded in framework |
| `views` / `outbox` / `automations` → `eventpoller.internal.*` | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low–Medium | Medium | ✗ **No** — `internal` boundary violated across modules |
| `commands` → `eventstore.internal.EventStoreConfig` | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low | ✓ Tolerable — low volatility |
| `views` / `outbox` / `automations` → `AbstractJdbcEventFetcher` (inheritance) | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | ✓ Yes — low distance offsets inheritance strength |
| `automations` → `commands` (`CommandExecutor` in `AutomationHandler` API) | [Model](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | ✓ Yes — intended integration point |
| `event-poller` → `eventstore` (`StoredEvent`, `Tag`) | [Model](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low | ✓ Yes — well-bounded model sharing |

---

## Issue: Domain Logic Contamination in a Generic Framework Module

**Integration**: `crablet-commands` ← wallet domain  
**Severity**: Critical

### Knowledge Leakage

`CommandExecutorImpl` contains the following code in `handleConcurrencyException`:

```java
// Fail fast: Wallet creation duplicates should throw exception
if ("open_wallet".equals(commandType)) {
    throw new ConcurrencyException(message, command, e);
}
```

This is [intrusive coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) in an unusual direction: instead of a consumer reaching into a module's private internals, domain-specific knowledge has penetrated the generic infrastructure layer. The framework module now encodes a business rule — "wallet creation duplicates are errors, not idempotent results" — that belongs exclusively in the wallet domain.

The leaked knowledge is implicit. There is no interface or contract that expresses this policy; it is buried inside exception-handling logic. The rule cannot be discovered through the public API of `crablet-commands`, and developers adopting the framework for a different domain (e.g., insurance, logistics) have no mechanism to express an equivalent policy for their own creation commands.

### Complexity Impact

When the wallet domain command type is renamed — a routine refactoring — this line silently stops working: duplicate wallet creation will return `idempotent` instead of throwing, and no compiler or test will catch the inconsistency unless the specific scenario is covered by an integration test. The [complexity](https://coupling.dev/posts/core-concepts/complexity/) here is that the cost of renaming a command type now extends into framework internals that a domain developer would not reasonably inspect.

Conversely, any developer reading `CommandExecutorImpl` to understand the generic framework behavior is forced to hold the wallet domain in working memory to understand why this branch exists — exceeding the 4 ± 1 units that cognitive capacity studies suggest is the limit for keeping context simultaneous.

### Cascading Changes

- **Renaming `open_wallet`** requires a matching change in `CommandExecutorImpl` to avoid silent behavioral regression.
- **Adding a new domain** that has its own entity-creation command (e.g., `create_policy`, `open_account`) finds no equivalent mechanism — the framework either swallows their duplicate-creation error as idempotent or requires adding another hardcoded branch into `CommandExecutorImpl`.
- **Removing the wallet example** from the repository would leave dead code and incorrect documentation comments in the core framework.
- As the framework moves toward active development and v1.0, this will appear in documentation and user onboarding, teaching a wrong mental model of how the command framework handles idempotency.

### Recommended Improvement

The [balance rule](https://coupling.dev/posts/core-concepts/balance/) calls for reducing strength when the coupling is problematic: introduce an explicit mechanism that lets each domain express its own idempotency policy instead of hardcoding it in the framework.

The most targeted fix is to extend the `CommandDecision.Idempotent` variant with a policy flag:

```java
// Domain decides: throw on duplicate (creation) or return silently (operations)
return Decision.idempotent(appendEvent, eventType, tagKey, entityId, OnDuplicate.THROW);
```

`CommandExecutorImpl` then dispatches on `decision.onDuplicate()` — generic behavior, no command names. The wallet handler for `OpenWalletCommand` passes `OnDuplicate.THROW`; every other handler passes `OnDuplicate.RETURN_IDEMPOTENT`. The framework has zero knowledge of command names; each domain owns its own duplicate policy.

The trade-off is a small API surface addition to `CommandDecision.Idempotent`. This is outweighed by removing permanently the only domain-specific branch in the framework's core.

---

## Issue: Processing Modules Import `event-poller` Internal Classes

**Integration**: `crablet-views` / `crablet-outbox` / `crablet-automations` → `com.crablet.eventpoller.internal.*`  
**Severity**: Significant

### Knowledge Leakage

All three processing module auto-configurations directly import and instantiate classes from `crablet-event-poller`'s `internal` package:

- `LeaderElectorImpl` — leader election via PostgreSQL advisory locks
- `EventProcessorImpl` — the generic poll-process-track engine
- `ProcessorManagementServiceImpl` — pause/resume/status management
- `InstanceIdProvider` — instance identity for lock naming

In Java, the `internal` package convention communicates: *this is an implementation detail; it may change without notice; do not depend on it from outside this module.* Three sibling modules freely cross that boundary. The `internal` package label provides false encapsulation: it appears to hide internal details, but it does not.

The shared knowledge is [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) — the configuration classes must know the exact constructor signatures, parameter ordering, and initialization semantics of the internal implementations. When `LeaderElectorImpl`'s constructor gains a new parameter, or when `EventProcessorImpl` is split into a specialized subtype, all three modules must be updated simultaneously.

### Complexity Impact

A developer working on `crablet-event-poller` sees the `internal` package and reasonably assumes they can refactor its contents freely — changing a constructor, extracting an interface, merging two classes — without needing to notify consumers. That assumption is wrong: three other modules will fail to compile. The [modularity](https://coupling.dev/posts/core-concepts/modularity/) signal from the `internal` label points in the opposite direction from the actual coupling.

Because the violation is in `@Configuration` classes (not in business logic), it is easy to overlook during code review and unlikely to be caught by unit tests.

### Cascading Changes

- **Any constructor change** to `EventProcessorImpl`, `LeaderElectorImpl`, or `ProcessorManagementServiceImpl` breaks the `@Configuration` class in every module that uses it. In a project with active development, internal constructors are the most likely thing to change during refactoring.
- **Splitting `EventProcessorImpl`** into variants (e.g., for batch vs. streaming) forces updates across three modules before any of them can compile.
- **Adding a fourth processing module** (e.g., a `crablet-sagas` module) has no documented contract to follow — it must reverse-engineer the existing `@Configuration` classes to discover which internal classes to use and how to instantiate them.

### Recommended Improvement

The [balance rule](https://coupling.dev/posts/core-concepts/balance/) prescribes reducing strength: introduce an [integration contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) in `crablet-event-poller`'s public API that encapsulates the internal construction details.

A `EventProcessorFactory` (or an `EventProcessorBuilder`) in `com.crablet.eventpoller` would expose a stable construction API:

```java
// In crablet-event-poller public API
public class EventProcessorFactory {
    public static <C, I> EventProcessor<C, I> create(
            EventProcessorSpec<C, I> spec,
            DataSource primary,
            DataSource read,
            TaskScheduler scheduler,
            ApplicationEventPublisher publisher) { ... }
}
```

Each processing module's `@Configuration` would call `EventProcessorFactory.create(...)` instead of `new EventProcessorImpl(...)`. The internal constructor signature becomes an implementation detail that `crablet-event-poller` can change freely. Any future module follows the same documented factory contract.

The trade-off is adding a factory to `event-poller`'s public API. This is a one-time cost that pays ongoing dividends as the three existing modules and any future modules gain a stable construction contract.

---

## Issue: `EventStoreConfig` Belongs in the Public API

**Integration**: `crablet-commands` → `com.crablet.eventstore.internal.EventStoreConfig`  
**Severity**: Minor

### Knowledge Leakage

`CommandExecutorImpl` declares `EventStoreConfig` as a required constructor parameter:

```java
public CommandExecutorImpl(EventStore eventStore,
                           List<CommandHandler<?>> commandHandlers,
                           EventStoreConfig config,   // ← from eventstore.internal
                           ClockProvider clock, ...)
```

Application developers wiring `CommandExecutorImpl` manually must import `com.crablet.eventstore.internal.EventStoreConfig` — a class from the `internal` package of another module — in their `@Configuration` class. This is [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) that crosses the `internal` package boundary of `crablet-eventstore`.

### Complexity Impact

The `internal` placement of `EventStoreConfig` is misleading: it is in the internal package, suggesting it is an implementation detail, but it is a required part of the public wiring API for the commands module. Framework users encounter an apparent contradiction — a Spring `@ConfigurationProperties` class that they must wire explicitly but that lives in a package they are not supposed to touch.

[Volatility](https://coupling.dev/posts/dimensions-of-coupling/volatility/) is low (config properties rarely change), so the practical impact is limited. This is technical debt that increases cognitive friction for new framework adopters rather than an active source of cascading changes.

### Cascading Changes

- **Renaming or moving `EventStoreConfig`** would break the `CommandExecutorImpl` constructor signature, requiring an update in any application that wires the command executor manually.
- **Adding Spring Boot auto-configuration** to `crablet-commands` (the natural next step as the framework matures) would need to reference an internal class to register the config bean, which is visible in any auto-configuration diagnostic output.

### Recommended Improvement

Move `EventStoreConfig` to `com.crablet.eventstore` (the public package). This is a single file relocation with a package declaration change. The class is already annotated `@ConfigurationProperties` and has no dependency on other internal eventstore classes, so no structural changes are required.

As a secondary improvement, consider providing a `crablet-commands` Spring Boot auto-configuration that wires `CommandExecutorImpl` automatically — removing the burden from application developers entirely and eliminating the internal class from user-visible wiring code.

---

_This analysis was performed using the [Balanced Coupling](https://coupling.dev) model by [Vlad Khononov](https://vladikk.com)._
