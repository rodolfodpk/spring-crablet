# Modularity Review — spring-crablet

> **Model:** Balanced Coupling (Vlad Khononov)  
> **Scope:** Entire codebase  
> **Date:** 2026-04-16  
> **Follow-up:** Findings §1 (`TopicPublisherPair` public API) and §3 (poller-backed `@AutoConfiguration` ordering) are implemented in the current codebase. The analysis below is kept as written for context.

---

## Executive Summary

The module boundaries in spring-crablet are sound. Public APIs are stable, internal
implementations are well-hidden behind factory methods and auto-configurations, and the
metrics layer is a textbook inversion-of-control design. One integration was concretely
unbalanced (`TopicPublisherPair` leaking via `OutboxManagementService`; see §1 — **since fixed**).
Everything else is either well-balanced or tolerable given the project's solo-developer context.

---

## Component Map

```
crablet-eventstore          core subdomain         low volatility
  └─ crablet-commands        supporting             low volatility
       └─ crablet-commands-web  generic (HTTP)      low volatility

crablet-event-poller        generic infrastructure  low volatility
  ├─ crablet-views           supporting             low-medium volatility
  ├─ crablet-outbox          supporting             low-medium volatility
  └─ crablet-automations     supporting (+ commands optional)  low-medium volatility

crablet-metrics-micrometer  generic (pub/sub)       very low volatility
crablet-test-support        generic (test utils)    very low volatility

shared-examples-domain      reference domain        medium volatility
wallet-example-app          application layer       high volatility
```

**Activation patterns:**

| Module | Activation mechanism |
|--------|----------------------|
| eventstore, commands, commands-web, event-poller | `@AutoConfiguration` — always active, `@ConditionalOnMissingBean` for overrides |
| views, outbox, automations | `@AutoConfiguration(after = EventPollerAutoConfiguration)` in `.imports` + `@ConditionalOnProperty` |

---

## Integration Analysis

### 1. `OutboxManagementService` leaks an internal type — **Unbalanced** *(resolved)*

**What happens (historical).**
`OutboxAutoConfiguration` uses `com.crablet.outbox.internal.TopicPublisherPair` as the
type parameter for every infrastructure bean it creates:

```java
// OutboxAutoConfiguration.java
ProgressTracker<TopicPublisherPair> outboxProgressTracker(...)
EventFetcher<TopicPublisherPair> outboxEventFetcher(...)
EventProcessor<OutboxProcessorConfig, TopicPublisherPair> outboxEventProcessor(...)
```

`OutboxManagementService` is a public class in `com.crablet.outbox.management`. It
implements `ProcessorManagementService<internal.TopicPublisherPair>`:

```java
// OutboxManagementService.java  — public class, internal type
public class OutboxManagementService implements ProcessorManagementService<TopicPublisherPair>
```

Any consumer that injects `OutboxManagementService` must import
`com.crablet.outbox.internal.TopicPublisherPair`. This is what `wallet-example-app`
does today (line 4 of `OutboxManagementController.java`).

Meanwhile, a public `com.crablet.outbox.TopicPublisherPair` exists and is never used.

**Coupling dimensions.**

| Dimension | Assessment |
|-----------|------------|
| Strength | **Model** — the internal type is present in every public method signature of `OutboxManagementService` |
| Distance | **Cross-module boundary** — the type escapes the library and must be imported by consumers |
| Volatility | **Low** — "identify a processor by (topic, publisher)" is a stable concept |

**Balance rule.**
`BALANCE = (STRENGTH XOR DISTANCE) OR NOT VOLATILITY`
= `(HIGH XOR HIGH) OR NOT LOW`
= `FALSE OR TRUE` = `TRUE`

The low volatility technically balances the equation, but the `XOR` leg is false — strength
and distance are both high. The correct reading here is: **the imbalance is tolerable today
because the concept is stable, but it is still an API design error**. Any consumer is forced
to depend on an implementation detail that has no reason to be visible.

**Root cause.**
The internal version was the original type used during development. When a public version was
added, the internal infrastructure was never migrated to use it.

**Fix.**
Delete `com.crablet.outbox.internal.TopicPublisherPair`. Use
`com.crablet.outbox.TopicPublisherPair` everywhere inside `OutboxAutoConfiguration`,
`OutboxManagementService`, `OutboxProgressTracker`, `OutboxEventFetcher`, and
`OutboxEventHandler`. Add input validation to the public record's compact constructor
(currently only present on the internal version). One compiler pass will find all callsites.

**Resolved.** `com.crablet.outbox.internal.TopicPublisherPair` was removed; infrastructure and consumers use `com.crablet.outbox.TopicPublisherPair` with compact-constructor validation.

---

### 2. `CommandWebAutoConfiguration` imported `command.internal.DiscoveredCommandRegistry` — **Acceptable** *(resolved)*

`CommandWebAutoConfiguration` (in `crablet-commands-web`) creates a
`DiscoveredCommandRegistry` by calling the static factory:

```java
// CommandWebAutoConfiguration.java
import com.crablet.command.DiscoveredCommandRegistry;  // now public

@Bean
public DiscoveredCommandRegistry discoveredCommandRegistry(List<CommandHandler<?>> handlers) {
    return DiscoveredCommandRegistry.fromHandlers(handlers);
}
```

**Coupling dimensions.**

| Dimension | Assessment |
|-----------|------------|
| Strength | **Functional** — creates the registry and passes it to `ExposedCommandTypeRegistry`; no business logic shared |
| Distance | **Adjacent declared dependency** — `crablet-commands-web` lists `crablet-commands` in its `pom.xml` |
| Volatility | **Low** — handler discovery mechanics are stable |

**Balance rule.**
`BALANCE = (FUNCTIONAL XOR LOW_DISTANCE) OR NOT LOW`
= `TRUE OR TRUE` = `TRUE`

Balanced. The strength is modest and the distance is low.

**Resolved.** `DiscoveredCommandRegistry` promoted from `com.crablet.command.internal` to `com.crablet.command`.

---

### 3. `views`, `outbox`, `automations` use `@Configuration` instead of `@AutoConfiguration` — **Correctness risk** *(resolved)*

`EventStoreAutoConfiguration`, `CommandAutoConfiguration`, `CommandWebAutoConfiguration`,
and `EventPollerAutoConfiguration` are all annotated `@AutoConfiguration` and can declare
ordering constraints via `after=`. ~~The three poller-backed add-ons use `@Configuration`:~~ *(Previously the poller-backed add-ons used `@Configuration` only:)*

```java
@Configuration                                     // views, outbox, automations (before fix)
@ConditionalOnProperty(name = "crablet.views.enabled", ...)
public class ViewsAutoConfiguration { ... }
```

~~They are listed in `.imports` — Spring Boot will process them — but they cannot declare
`after = EventPollerAutoConfiguration.class`. Ordering currently works because Spring
resolves bean dependencies at runtime, but there is no explicit contract.~~

**Practical consequence (historical).** If a future Spring Boot version changes when `@Configuration`
classes in the `.imports` file are resolved relative to `@AutoConfiguration` classes, the
poller modules could try to construct beans before `EventPollerAutoConfiguration` has run.

**Fix.** Replace `@Configuration` with `@AutoConfiguration(after = EventPollerAutoConfiguration.class)` in `ViewsAutoConfiguration`, `OutboxAutoConfiguration`, and `AutomationsAutoConfiguration`.

**Resolved.** Those three classes now use `@AutoConfiguration(after = EventPollerAutoConfiguration.class)` while remaining registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

---

### 4. `shared-examples-domain` ↔ framework test scope (build cycle) — **Tolerable for solo, friction for contributors**

Framework modules (`crablet-commands`, `crablet-views`, etc.) use `shared-examples-domain`
in test scope to run realistic domain tests. `shared-examples-domain` depends on
`crablet-eventstore` and `crablet-commands` to compile. This creates a cycle that Maven
cannot resolve on its own, requiring stub JARs and a Makefile build order.

**Coupling dimensions.**

| Dimension | Assessment |
|-----------|------------|
| Strength | **Model** — framework tests instantiate real wallet types; a `CommandHandler` API change propagates to `shared-examples-domain` |
| Distance | **Same repo, same developer** — low effective distance |
| Volatility | **Medium** — the framework public API is actively evolving |

**Balance rule.**
Model strength + low distance + medium volatility: `(MEDIUM XOR LOW) = TRUE` — balanced for
the current setup. Distance is the saving factor.

**Where this becomes a problem.** If a second developer joins and touches only one module,
they inherit the full build ceremony. If the example domain is ever extracted as a separate
repository, the distance increases and this integration becomes unbalanced. The current design
is correct for a solo project and requires no immediate change.

---

## What Is Working Well

**Pub/sub metrics (exemplary design).**
`crablet-metrics-micrometer` has zero knowledge of how events are generated. It subscribes to
`@EventListener` methods for metric events published via `ApplicationEventPublisher`. Each
publishing module declares its own metric event types with no knowledge of who listens.
Strength: contract. Distance: cross-module. Volatility: very low. Perfectly balanced.

**Factory abstractions over implementations.**
`CommandExecutors.create(...)` and `EventProcessorFactory.createProcessor(...)` mean that
`CommandExecutorImpl` and `EventProcessorImpl` are never referenced by name outside their
own modules. The internal package boundary is real, not just a naming convention.

**Optional dependencies done right.**
`AutomationsAutoConfiguration` uses `ObjectProvider<CommandExecutor>` with a clear startup
failure message when in-process handlers are declared without a `CommandExecutor` bean. The
optional coupling is explicit, validated early, and documented in the error message.

**Activation by inclusion.**
`crablet-commands-web` is activated by adding it to `pom.xml` and declaring one required bean
(`CommandApiExposedCommands`). If the bean is absent, Spring Boot fails at startup with a
clear missing-bean message — no silent misconfiguration. This is a better opt-in mechanism
than the `@ConditionalOnProperty` pattern used by the poller modules.

---

## Prioritized Recommendations

| Priority | Finding | Action |
|----------|---------|--------|
| **Done** | `OutboxManagementService` leaked `internal.TopicPublisherPair` into public API | Single public `TopicPublisherPair` with validation; internal duplicate removed |
| **Done** | `views`/`outbox`/`automations` used `@Configuration` instead of `@AutoConfiguration` | Now `@AutoConfiguration(after = EventPollerAutoConfiguration.class)` |
| **Done** | `DiscoveredCommandRegistry` was in `command.internal` but used by a sibling module | Promoted to `com.crablet.command` |
| **Note** | `shared-examples-domain` ↔ framework test cycle requires stub JARs | Acceptable for solo development; revisit if a contributor joins or the example domain is extracted |
