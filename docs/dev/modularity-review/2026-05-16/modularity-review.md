# Modularity Review

**Scope**: Entire codebase — all framework modules, shared examples domain, embabel-codegen  
**Date**: 2026-05-16

## Executive Summary

Spring-Crablet is a Java 25 event-sourcing framework for Spring Boot that layers commands, views, automations, outbox publication, and polling infrastructure on top of a PostgreSQL event store using DCB-style consistency boundaries. The overall modularity is healthy: the core dependency graph is clean and well-directed, the public APIs are clearly separated from internal implementations, and the optional-module design pattern (independently adoptable JARs) is coherent. Two issues warrant attention before the 1.0 API freeze. The most significant is `crablet-metrics-micrometer`, which structurally undermines the optional-module promise by pulling the entire module graph onto the classpath through [model-level coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) to every other module's metric event types. A secondary issue is that the shared-fetch extension API is mislabeled as `internal`, obscuring a real cross-module contract that three modules depend on.

## Coupling Overview Table

| Integration | [Strength](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | [Distance](https://coupling.dev/posts/dimensions-of-coupling/distance/) | [Volatility](https://coupling.dev/posts/dimensions-of-coupling/volatility/) | [Balanced?](https://coupling.dev/posts/core-concepts/balance/) |
|---|---|---|---|---|
| views / automations / outbox → eventstore public API | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | Yes |
| views / automations / outbox → event-poller public API | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | Yes |
| views / automations / outbox auto-configs → event-poller `internal.sharedfetch` | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | Partial — see Issue 2 |
| automations → commands (`CommandExecutor`) | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | Yes |
| automations → views (`ViewSubscriptionLookup` indirection) | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low | Yes |
| metrics-micrometer → all other modules | [Model](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low (intended: independent) | High | **No — Issue 1** |
| ViewBackedAutomationHandler → views classpath (runtime) | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low | Yes — see Issue 3 |

---

<div class="issue">

## Issue 1: `crablet-metrics-micrometer` destroys optional-module independence

**Integration**: `crablet-metrics-micrometer` → `crablet-eventstore`, `crablet-commands`, `crablet-event-poller`, `crablet-views`, `crablet-automations`, `crablet-outbox`  
**Severity**: Critical

### Knowledge Leakage

`MicrometerMetricsCollector` directly imports specific metric event record types from every other module's `metrics` sub-package: `AutomationExecutionMetric`, `AutomationExecutionErrorMetric`, `ViewProjectionMetric`, `ViewProjectionErrorMetric`, `OutboxErrorMetric`, `EventsPublishedMetric`, `CommandSuccessMetric`, `CommandFailureMetric`, `BackoffStateMetric`, `LeadershipMetric`, and more. Each of these is a concrete record type with specific fields (`commandType`, `operationType`, `viewName`, `duration`, etc.) that belong to the internal metric-reporting vocabulary of their respective module.

This is [model coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/): the collector does not observe a stable contract — it reaches directly into the shape of each module's internal event payloads. Every field name and every event type name becomes a cross-module dependency.

### Complexity Impact

The framework's stated design principle is that modules are independently adoptable: a team using only `crablet-eventstore` and `crablet-commands` should not need to carry `crablet-views`, `crablet-automations`, or `crablet-outbox` on their classpath. `crablet-metrics-micrometer` breaks this promise silently. Adding it to a project that only uses the command side transitively forces all optional modules onto the classpath, increasing startup time, requiring Flyway migrations for tables the application never uses, and triggering auto-configurations the team never opted into.

The collector is also a [complexity](https://coupling.dev/posts/core-concepts/complexity/) amplifier for contributors: every new module added to the framework needs a corresponding `@EventListener` handler added to this single class. The class already handles seven distinct modules. It will grow monotonically.

### Cascading Changes

Concrete cascade scenarios:

- **Rename a metric event field** (e.g., `viewName` → `projectionName` in `ViewProjectionMetric`): requires a coordinated change in both `crablet-views` and `crablet-metrics-micrometer`.
- **Add a new metric event type** to `crablet-automations` (e.g., `AutomationSkippedMetric`): must be imported and handled in `MicrometerMetricsCollector` even if the automation module is otherwise self-contained.
- **Add a new framework module** (e.g., `crablet-sagas`): the metrics module must be updated to pull it in before metrics work for that module, coupling the release cycles.

### Recommended Improvement

Replace the single monolithic collector with **per-module metric listeners** that activate conditionally on each module's presence:

```
crablet-eventstore/metrics/   → EventstoreMetricsListener  (always active)
crablet-commands/metrics/     → CommandMetricsListener     (active when commands present)
crablet-views/metrics/        → ViewMetricsListener        (active when views present)
crablet-automations/metrics/  → AutomationMetricsListener  (active when automations present)
crablet-outbox/metrics/       → OutboxMetricsListener      (active when outbox present)
crablet-event-poller/metrics/ → PollerMetricsListener      (active when poller present)
```

Each listener lives inside its own module and subscribes only to that module's metric events. `crablet-metrics-micrometer` becomes a thin orchestrator that conditionally activates listeners via `@ConditionalOnClass` (or simply disappears — each module publishes its own metrics when Micrometer is present).

**Trade-off**: this splits one class into six. The benefit is that each listener compiles, tests, and ships with its module — adding metrics to a new module is a change in one place, not two. The optional-module promise is restored: teams get exactly the metrics for the modules they use, with no phantom classpath pull-in.

If a full split is too much work before 1.0, a lower-cost intermediate step is to introduce a shared `MetricEvent` marker interface (already present in `crablet-eventstore` as `MetricEvent`) and have the collector listen on that interface alone, delegating dispatch to a registry. This reduces the number of field-level imports and makes adding new event types additive rather than requiring changes to the collector class.

</div>

---

<div class="issue">

## Issue 2: `sharedfetch` is an inter-module extension API mislabeled as `internal`

**Integration**: `crablet-views`, `crablet-automations`, `crablet-outbox` auto-configs → `com.crablet.eventpoller.internal.sharedfetch`  
**Severity**: Significant

### Knowledge Leakage

The `com.crablet.eventpoller.internal.sharedfetch` package contains `SharedFetchModuleProcessor`, `ModuleScanProgressRepository`, and `ProcessorScanProgressRepository`. All three are imported and instantiated directly by `ViewsAutoConfiguration`, `AutomationsAutoConfiguration`, and `OutboxAutoConfiguration`.

`SharedFetchModuleProcessor` is instantiated via a constructor with more than 15 parameters, including implementation-specific arguments like `fetchBatchSize`, `clockProvider`, and `wakeupSource`. This is [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/): the consuming auto-configs know not just that a shared-fetch processor exists, but exactly how to construct one — every parameter is a detail that must stay synchronized across four files when the constructor changes.

The `internal` package name sends the signal "this is an implementation detail of `crablet-event-poller`, not consumed outside." In reality it is consumed in three sibling modules. The name misrepresents the actual [integration contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/).

### Complexity Impact

For framework contributors, the `internal` label implies freedom to refactor: rename a class, restructure the constructor, split a repository — without worrying about external callers. That assumption is false. Any such change silently breaks the three auto-configs in sibling modules, which will only be caught at test time or at application startup. The label creates a false sense of encapsulation while the actual [coupling](https://coupling.dev/posts/core-concepts/coupling/) is real.

As the framework grows and new modules want the shared-fetch optimization, they face an undocumented choice: use the "internal" package (and risk breaking if it changes) or re-implement the optimization themselves (duplication). Neither path is good.

### Cascading Changes

Concrete cascade scenarios:

- **Add a parameter to `SharedFetchModuleProcessor`** (e.g., a retry policy): must update three auto-configs simultaneously, with no compiler warning that the internal package has external callers.
- **A new framework contributor refactors `sharedfetch`** believing the `internal` label protects callers: breaks views, automations, and outbox in ways that don't surface until integration tests run.
- **Add a fourth module** (e.g., `crablet-sagas`) that wants shared-fetch: no documented extension point to follow; the contributor must discover the pattern by reading existing auto-configs.

### Recommended Improvement

Promote the shared-fetch types to a proper, named package within `crablet-event-poller` that signals their role as an extension API for module auto-configurations:

```
com.crablet.eventpoller.sharedfetch.SharedFetchModuleProcessor
com.crablet.eventpoller.sharedfetch.ModuleScanProgressRepository
com.crablet.eventpoller.sharedfetch.ProcessorScanProgressRepository
```

Add a `package-info.java` with a clear javadoc contract: "This package is the extension API for Crablet module processors that use the shared-fetch optimization. It is intended for use by Crablet auto-configuration classes only, not by application code."

**Trade-off**: renaming packages is a compile-break change in three modules and requires a minor version bump if any of these types are accidentally on the public API surface. The benefit is that the `internal` label no longer lies: what is `internal` is internal, what is a cross-module extension API is labeled as such. Future contributors will understand at a glance which path to follow. The existing functional coupling strength stays the same — only the clarity of the contract improves.

</div>

---

<div class="issue">

## Issue 3: `ViewBackedAutomationHandler`'s classpath requirement is invisible at the interface level

**Integration**: `crablet-automations` `ViewBackedAutomationHandler` → `crablet-views` classpath (runtime startup)  
**Severity**: Minor

### Knowledge Leakage

`ViewBackedAutomationHandler` declares `Set<String> getReadViewNames()` instead of `Set<String> getEventTypes()`, promising that the framework will derive wake events from view subscriptions at startup. This interface carries an implicit runtime requirement: `crablet-views` must be on the classpath, and a `ViewSubscription` bean with the declared view name must exist.

Neither requirement is visible from the interface itself. The consequence of not meeting them is a startup exception, not a compile error. The knowledge that "implementing this interface requires views on the classpath" is hidden in the auto-configuration internals and in the Maven POM comment (`<!-- optional — enables view-backed automation wake-event inference -->`), not in the interface contract.

### Complexity Impact

For application developers implementing `ViewBackedAutomationHandler`, the failure mode is a startup crash with a diagnostic message in `AutomationDefinitionResolver`. The error is clear, but it arrives late (boot time) rather than at the point of the mistake (adding the dependency). Teams adopting `crablet-automations` without reading the dependency notes will encounter this only when they first implement a view-backed automation.

### Cascading Changes

This is low [volatility](https://coupling.dev/posts/dimensions-of-coupling/volatility/): the classpath requirement is stable and the interface itself is a clean contract. No cascading changes occur as the framework evolves. The issue is a usability friction point, not a structural coupling problem.

### Recommended Improvement

Add a javadoc note directly to the `ViewBackedAutomationHandler` interface declaration:

```java
/**
 * ...
 * <p><strong>Classpath requirement:</strong> {@code crablet-views} must be on the classpath,
 * and a {@link com.crablet.views.ViewSubscription} bean matching each name in
 * {@link #getReadViewNames()} must be registered. Startup fails with a descriptive
 * message if either condition is not met.
 */
```

**Trade-off**: documentation only — no structural change required. The coupling between automations and views for this feature is intentional and the `ViewSubscriptionLookup` indirection is already well-designed. A Javadoc note moves the signal from the POM comment (where few readers look) to the interface (where all implementors look), at essentially zero cost.

</div>

---

_This analysis was performed using the [Balanced Coupling](https://coupling.dev) model by [Vlad Khononov](https://vladikk.com)._
