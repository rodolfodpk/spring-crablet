# Upgrade Guide

Step-by-step migration notes for each breaking change in the `1.0.0-SNAPSHOT` series. Changes are listed newest first.

Crablet uses `@Stable` as a documented compatibility signal for application-facing APIs. It is
not currently enforced by tooling. After `1.0`, breaking changes to stable APIs should be covered
here, and should use deprecation first when that is practical.

---

## `EventProcessorFactory.createProcessor` — positional overloads replaced by `ProcessorSpec`

**Affects:** Code that creates event processors directly; Spring Boot auto-configuration users are
already migrated internally.

### What changed

All positional `EventProcessorFactory.createProcessor(...)` overloads were removed. The single
entry point now accepts a named, validated `ProcessorSpec`. The spec requires exactly one
leader-election strategy: either an existing `LeaderElector`, or all four raw settings
(`processorName`, `lockKey`, `instanceId`, and `writeDataSource`). It also exposes
`clockProvider(...)` for deterministic tests and defaults wakeup, poller configuration, clock, and
selections when they are omitted.

The shorter `SharedFetchModuleProcessor` constructors were also removed. Direct callers must now
provide both `ProcessorWakeupSource` and `ClockProvider` to its full constructor.

### Migration

```java
// Before:
EventProcessorFactory.createProcessor(
        configs, leaderElector, progressTracker, eventFetcher, eventHandler,
        taskScheduler, eventPublisher, wakeupSourceFactory, eventPollerConfig,
        selections);

// After:
EventProcessorFactory.createProcessor(
        ProcessorSpec.<MyConfig, String>builder()
                .configs(configs)
                .leaderElector(leaderElector)
                .progressTracker(progressTracker)
                .eventFetcher(eventFetcher)
                .eventHandler(eventHandler)
                .taskScheduler(taskScheduler)
                .eventPublisher(eventPublisher)
                .wakeupSourceFactory(wakeupSourceFactory)
                .eventPollerConfig(eventPollerConfig)
                .selections(selections)
                .build());
```

To have the factory create the elector, replace `.leaderElector(...)` with
`.processorName(...)`, `.lockKey(...)`, `.instanceId(...)`, and `.writeDataSource(...)`.

---

## `DCBViolation.errorCode()` — `String` replaced by `DCBErrorCode`

**Affects:** Code that compares or stores the error code from `DCBViolation`.

### What changed

`DCBViolation.errorCode()` now returns the enum `DCBErrorCode`, preventing misspelled string
comparisons. PostgreSQL error values that are absent or unknown are conservatively mapped to
`DCBErrorCode.CONCURRENCY_VIOLATION`.

### Migration

```java
// Before:
"IDEMPOTENCY_VIOLATION".equals(violation.errorCode())

// After:
violation.errorCode() == DCBErrorCode.IDEMPOTENCY_VIOLATION
```

---

## `EventStore.appendConditional` — combined concurrency+idempotency append (additive)

**Affects:** No existing code — purely additive default method. Custom `EventStore`
implementations get a safe (non-atomic) fallback automatically; override it if you need the
atomicity guarantee the bundled PostgreSQL and in-memory implementations provide.

### What changed

A new low-level method composes a concurrency check (with stream position) and an idempotency
check into a single call, used internally by `CommandExecutorImpl` for
`CommandDecision.CommutativeGuarded`:

```java
String appendConditional(List<AppendEvent> events, AppendCondition condition);
```

`EventStore.appendNonCommutative(events, AppendCondition)` now delegates to it, which also fixes a
pre-existing bug where that overload silently dropped `condition.idempotencyQuery()`.

As a related fix, `append_events_if()`'s DCB conflict check now takes a decision-model-keyed
advisory lock (mirroring the existing idempotency lock) to close a race where two genuinely
concurrent callers racing the same condition could otherwise both succeed under `READ_COMMITTED`.
This protects both the standalone `EventStore` append path and command-handler-driven appends via
`CommandExecutor` uniformly.

### Migration

No action required.

---

## `EventProcessorFactory.createProcessor` — 4 trailing `Set<String>` params replaced with `Collection<? extends EventSelection>` (superseded)

**Affects:** Any code that calls the widest `EventProcessorFactory.createProcessor` overload directly
(most applications go through `crablet-outbox`/`crablet-views`/`crablet-automations` auto-configuration
and are unaffected)

### What changed

The widest overload in each `createProcessor` family (LeaderElector-supplied, and raw
election-params) took 4 trailing parameters — `Set<String> subscribedEventTypes, requiredTagKeys,
anyOfTagKeys, exactTagKeys` — an argument-transposition risk, since all four are same-typed and
adjacent. Both are replaced by a single `Collection<? extends EventSelection> selections`
parameter. `EventSelection` (already implemented by `TopicConfig`, `ViewSubscription`, and
`AutomationDefinition`) now carries 4 new `static` union helpers
(`unionEventTypes`/`unionRequiredTags`/`unionAnyOfTags`/`unionExactTagKeys`) that
`EventProcessorFactory` uses internally to derive the same 4 filters — removing logic that was
previously copy-pasted identically across `OutboxAutoConfiguration`, `ViewsAutoConfiguration`, and
`AutomationsAutoConfiguration`.

### Migration

```java
// Before:
EventProcessorFactory.createProcessor(
        configs, leaderElector, progressTracker, eventFetcher, eventHandler,
        taskScheduler, eventPublisher, wakeupSourceFactory, eventPollerConfig,
        moduleEventTypes(selections), moduleRequiredTagKeys(selections),
        moduleAnyOfTagKeys(selections), moduleExactTagKeys(selections));

// After:
EventProcessorFactory.createProcessor(
        configs, leaderElector, progressTracker, eventFetcher, eventHandler,
        taskScheduler, eventPublisher, wakeupSourceFactory, eventPollerConfig,
        selections); // Collection<? extends EventSelection>, e.g. topicConfigs.values()
```

This intermediate pre-release API was subsequently replaced by `ProcessorSpec`; use the migration
at the top of this guide. No action is required if you only rely on the module auto-configuration
beans.

---

## Consolidated framework migrations, `crablet_event_tags`, and command-level idempotency

**Affects:** Fresh test and example databases; users of `CommandExecutor` who want idempotency

### What changed

Framework Flyway history was consolidated before a stable release. Fresh databases now receive the final framework schema through:

```text
V1__crablet_eventstore_schema.sql
```

The final schema includes the `crablet_event_tags` derived table and command-level idempotency shape from the start.

**`crablet_event_tags` derived table**

A normalized `crablet_event_tags` table exists alongside `crablet_events`. Each row represents one `key=value` tag pair from one event.

- Per-processor poller tag filtering now uses indexed `crablet_event_tags` EXISTS subqueries instead of `unnest(crablet_events.tags)` array scans. Exact tag filters use `(key, value, position)`; broad key-existence filters use `(key, position)`.
- Idempotency and DCB conflict checks inside `append_events_if` continue to use `crablet_events.tags @>` with the GIN index — real decision models use 2+ tags per criterion, so the GIN path handles the common case directly.
- `crablet_events` remains the canonical source of truth; `crablet_event_tags` is derived and kept in sync atomically via the same CTE that inserts into `crablet_events`.
- **Write amplification:** every append writes one `crablet_event_tags` row per tag per event. See `docs/user/PERFORMANCE.md` for tradeoff details and drift-check queries.

**Command-level idempotency**

`crablet_commands` has a `command_id UUID PRIMARY KEY`. `transaction_id` is retained as a regular indexed column for event-to-command linkage. No `idempotency_key` column is added.

A new `CommandExecutor` overload is available for client-controlled idempotency:

```java
// use CommandExecutionOptions.builder() to set correlationId, commandId, or both
executor.execute(command, CommandExecutionOptions.builder()
        .commandId(commandId)   // UUID v7 recommended for time-ordered B-tree keys
        .build());

// combine with correlation ID
executor.execute(command, CommandExecutionOptions.builder()
        .correlationId(correlationId)
        .commandId(commandId)
        .build());
```

When `commandId` is supplied, the executor inserts the command record using that UUID as the primary key **before the handler runs**. If a committed record with that ID already exists, the handler is not re-run and `ExecutionResult.wasIdempotent()` returns `true`. On rollback the row rolls back atomically — the ID is released and the next attempt proceeds as new.

Requires `crablet.eventstore.persist-commands=true`. An `InvalidCommandException` is thrown if persistence is disabled and a `commandId` is supplied.

No action required if you do not use the new overload. Existing `execute(T)` and `execute(T, CommandHandler<T>)` calls are unchanged. The previously deprecated `execute(T, @Nullable UUID correlationId)` overload has been removed — migrate to `execute(T, CommandExecutionOptions.builder().correlationId(id).build())` if you were using it.

Because this consolidation rewrites pre-release Flyway history, discard and recreate any local database that already applied the old framework V1-V10 chain. The project uses Testcontainers for framework tests, so this mainly affects manually created local example databases.

---

## `TopicConfig` — public constructors removed, use the builder

**Affects:** Any code that called `new TopicConfig(...)` directly instead of `TopicConfig.builder(name)`

### What changed

`TopicConfig`'s two public constructors (a 5-arg overload defaulting `eventTypes` to "match all",
and the 6-arg field-assignment constructor) have been removed. `TopicConfig.builder(name)` was
already the documented, exclusively-used construction path in this repo's own tests and
call sites; the raw constructors offered no capability the builder didn't, while adding four
consecutive `Set<String>` parameters that were easy to transpose silently.

### Migration

```java
// Before:
new TopicConfig(name, eventTypes, requiredTags, anyOfTags, exactTags, publishers);

// After:
TopicConfig.builder(name)
        .eventTypes(eventTypes)
        .requireTags(requiredTags.toArray(new String[0]))
        .anyOfTags(anyOfTags.toArray(new String[0]))
        .publishers(publishers.toArray(new String[0]))
        // exactTags: call .exactTag(key, value) per entry
        .build();
```

---

## `TopicPublisherPair` — moved from `.internal` to public package

**Affects:** Any code that imports `com.crablet.outbox.internal.TopicPublisherPair`

### What changed

`TopicPublisherPair` is now a public type at `com.crablet.outbox.TopicPublisherPair`. The `.internal` path no longer exists.

### Migration

Update the import:

```java
// Before:
import com.crablet.outbox.internal.TopicPublisherPair;

// After:
import com.crablet.outbox.TopicPublisherPair;
```

---

## `StreamPosition.of(long)` — factory removed

**Affects:** Any code calling `StreamPosition.of(long)` directly

### What changed

The `StreamPosition.of(long position)` factory was removed. `StreamPosition` now carries additional fields (timestamp, transaction ID) that cannot be reconstructed from a position number alone.

### Migration

You should not be constructing `StreamPosition` from a raw `long` — it must come from a projection result. Use `StreamPosition.zero()` as a starting point, or capture the position from `ProjectionResult.streamPosition()`:

```java
// Before (incorrect — reconstructed from raw value):
StreamPosition pos = StreamPosition.of(42L);

// After — capture from projection:
ProjectionResult<MyState> result = eventStore.project(query, projector);
StreamPosition pos = result.streamPosition();

// Or start from zero for a full projection:
eventStore.project(query, StreamPosition.zero(), projector);
```

---

## `WriteDataSource` / `ReadDataSource` — typed beans replace `@Qualifier DataSource`

**Affects:** Any Spring `@Configuration` class that injected `@Qualifier("primaryDataSource") DataSource` or `@Qualifier("readDataSource") DataSource`

### What changed

The framework now uses typed wrapper beans `WriteDataSource` and `ReadDataSource` instead of raw `DataSource` beans with Spring qualifiers. This removes the qualifier coupling and makes the write/read split explicit at the type level.

### Migration

Replace qualified `DataSource` parameters with the typed wrappers:

```java
// Before:
@Bean
public MyComponent myComponent(
        @Qualifier("primaryDataSource") DataSource writeDataSource,
        @Qualifier("readDataSource") DataSource readDataSource) { ... }

// After:
@Bean
public MyComponent myComponent(
        WriteDataSource writeDataSource,
        ReadDataSource readDataSource) { ... }
```

If you need the underlying `javax.sql.DataSource`, call `.dataSource()` on the wrapper.

---

## `crablet.event-poller.notifications.enabled` — property removed

**Affects:** Any configuration setting `crablet.event-poller.notifications.enabled=true`

### What changed

The `enabled` boolean toggle was removed from notification properties. LISTEN wakeup is now enabled implicitly by the presence of `jdbc-url`. No `enabled` flag is needed.

### Migration

Remove the `enabled` property. If you set `jdbc-url`, wakeup is active. If you omit it, the poller falls back to pure scheduled polling:

```yaml
# Before:
crablet.event-poller.notifications.enabled: true
crablet.event-poller.notifications.jdbc-url: jdbc:postgresql://db:5432/mydb

# After:
crablet.event-poller.notifications.jdbc-url: jdbc:postgresql://db:5432/mydb
```

---

## `CommandHandler.Decision` inner record — removed

**Affects:** Command handlers that returned `Decision.of(event)` from `decide()`

### What changed

The `Decision` inner record on `CommandHandler` was removed. Handlers must now return the appropriate `CommandDecision` variant directly, matching the sub-interface they implement.

### Migration

Replace `Decision.of(event)` with the correct `CommandDecision` variant:

```java
// Before:
return Decision.of(depositEvent);

// After — choose the variant that matches your handler type:
return CommandDecision.Commutative.of(depositEvent);          // CommutativeCommandHandler
return CommandDecision.NonCommutative.of(event, query, pos);  // NonCommutativeCommandHandler
return CommandDecision.Idempotent.of(event, type, key, val);  // IdempotentCommandHandler
```

---

## `CommutativeCommandHandler.decide()` — return type narrowed to `CommutativeDecision`

**Affects:** Any class implementing `CommutativeCommandHandler`

### What changed

`CommutativeCommandHandler.decide()` previously returned the parent `CommandDecision` sealed type. It now returns `CommandDecision.CommutativeDecision`, a new sealed intermediate type that permits only `Commutative` and `CommutativeGuarded`. This makes the contract consistent with `NonCommutativeCommandHandler` and `IdempotentCommandHandler`, and prevents accidentally returning a `NonCommutative` or `Idempotent` decision from a commutative handler.

### Migration

No code changes are required if you were already returning the correct variant. The compiler will flag any handler that was returning a non-commutative decision type — treat that as a bug to fix, not as churn.

```java
// Before — compiled but was too permissive:
@Override
public CommandDecision decide(EventStore eventStore, DepositCommand command) {
    return CommandDecision.Commutative.of(event);
}

// After — return type is now narrowed:
@Override
public CommandDecision.CommutativeDecision decide(EventStore eventStore, DepositCommand command) {
    return CommandDecision.Commutative.of(event);
}
```

---

## `CommandDecision.Idempotent` — `OnDuplicate` policy

**Affects:** Code that uses `CommandDecision.Idempotent` or `IdempotentCommandHandler`

### What changed

`CommandDecision.Idempotent` gained an `onDuplicate` field controlling what happens when a duplicate is detected. The default is `OnDuplicate.RETURN_IDEMPOTENT` (previous behavior — silent success). Use `OnDuplicate.THROW` for entity-creation commands where a duplicate indicates a real conflict.

### Migration

Existing handlers that call `Idempotent.of(event, eventType, tagKey, tagValue)` continue to work unchanged (defaults to `RETURN_IDEMPOTENT`). To opt into strict duplicate rejection:

```java
// Throw on duplicate (e.g. OpenWalletCommand — wallet must be unique)
return CommandDecision.Idempotent.of(event, type(WalletOpened.class),
        WALLET_ID, command.walletId(), OnDuplicate.THROW);
```

---

## `EventStore.appendIdempotent` — `Query` overload added

**Affects:** Code calling `EventStore.appendIdempotent` directly (outside the command framework)

### What changed

A second overload was added:

```java
String appendIdempotent(List<AppendEvent> events, Query idempotencyQuery);
```

The original raw-string signature (`eventType`, `tagKey`, `tagValue`) is still available. The new overload is preferred when the idempotency criteria involve multiple tags or when consistency with `appendNonCommutative` is desired.

### Migration

No migration required — the raw-string signature is unchanged. Migrate to the `Query` overload at your convenience:

```java
// Before:
eventStore.appendIdempotent(events, type(WalletOpened.class), WALLET_ID, walletId);

// After (equivalent, using Query):
eventStore.appendIdempotent(events,
        QueryBuilder.builder().event(type(WalletOpened.class), WALLET_ID, walletId).build());
```

---

## AutomationHandler API — `react()` → `decide()`

**Affects:** Any class implementing `AutomationHandler`

### What changed

`AutomationHandler.react(StoredEvent)` was replaced by `AutomationHandler.decide(StoredEvent)` which returns `List<AutomationDecision>` instead of `void`.

### Migration

**Before:**
```java
@Override
public void react(StoredEvent stored) {
    // call command or external endpoint directly
    commandExecutor.execute(new MyCommand(...));
}
```

**After:**
```java
@Override
public List<AutomationDecision> decide(StoredEvent stored) {
    // return a decision — CommandExecutor executes it
    return List.of(new AutomationDecision.ExecuteCommand(new MyCommand(...)));
}
```

See [`crablet-automations/README.md`](../../crablet-automations/README.md) and [`crablet-outbox/README.md`](../../crablet-outbox/README.md).

---

## Metrics — label and metric name renames

**Affects:** Prometheus alerts and Grafana dashboards using Crablet metrics

### What changed

Two breaking label/metric renames in `crablet-metrics-micrometer`:

| Old | New | Scope |
|---|---|---|
| `processor_is_leader{instance="..."}` | `processor_is_leader{instance_id="..."}` | All processors |
| `outbox_is_leader` | `processor_is_leader{processor="outbox"}` | Outbox only |

### Migration

1. **Grafana panels** — find any panel using `outbox_is_leader` and replace with `processor_is_leader{processor="outbox"}`.
2. **Prometheus alerts** — replace `instance` label with `instance_id` in all alert rules referencing `processor_is_leader`.
3. **Import the updated dashboard** — re-import `observability/grafana/dashboards/crablet-dashboard.json` which uses the new names.

---

## AutomationSubscription removed

**Affects:** Code that registered automations via `AutomationSubscription`

### What changed

`AutomationSubscription` has been removed. Event selection (which event types and tags trigger an automation) is now declared directly on the `AutomationHandler` via `getEventTypes()` and `getRequiredTags()`.

### Migration

**Before:**
```java
@Bean
public AutomationSubscription mySubscription() {
    return AutomationSubscription.builder()
        .eventTypes(Set.of(type(MyEvent.class)))
        .requiredTags(Set.of(MY_TAG))
        .build();
}
```

**After:**
```java
@Component
public class MyAutomationHandler implements AutomationHandler {

    @Override
    public Set<String> getEventTypes() {
        return Set.of(type(MyEvent.class));
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of(MY_TAG);
    }

    @Override
    public List<AutomationDecision> decide(StoredEvent stored) { ... }
}
```

---

## Shared-fetch progress schema

**Affects:** Deployments enabling `*.shared-fetch.enabled=true`

### What changed

Shared-fetch mode requires the framework scan-progress tables:

```text
crablet_module_scan_progress
crablet_processor_scan_progress
```

Fresh installs receive these through the consolidated `V1__crablet_eventstore_schema.sql`.

### Migration

Shared-fetch is opt-in. If you enable it:

1. Ensure the framework poller progress schema is applied before deploying.
2. Set `crablet.views.shared-fetch.enabled=true` (or equivalent for automations/outbox).
3. Existing `scan_progress` rows are preserved — no data migration needed.

If you are not using shared-fetch, no action is required.

---

## EventHandler — DataSource constructor parameter removed

**Affects:** Custom `EventHandler<I>` implementations that accepted a raw `DataSource`

### What changed

The generic `EventHandler<I>` no longer accepts a raw `DataSource` in its constructor. Write-database access for views is owned by `AbstractViewProjector` / `AbstractTypedViewProjector` which inject `WriteDataSource` directly.

### Migration

Remove any `DataSource` constructor parameter from custom `EventHandler` implementations. If you need write access to a view table, extend `AbstractTypedViewProjector` or `AbstractViewProjector` instead.

---

## AppendCondition fluent factory + AppendEvent.of concise factory (additive)

**Affects:** No existing code — purely additive overloads.

### What changed

Two ergonomic shorthand APIs inspired by UmaDB's Java client were added:

**`AppendCondition.failIfChanged(decisionModel).after(streamPosition)`** — replaces the positional
`appendNonCommutative(events, decisionModel, streamPosition)` call with a named condition that reads
left-to-right as a sentence:

```java
// Before (still works)
return NonCommutative.of(event, decisionModel, projection.streamPosition());

// After (preferred)
return NonCommutative.of(event,
        AppendCondition.failIfChanged(decisionModel).after(projection.streamPosition()));
```

**`AppendEvent.of(type, tagKey, tagValue, data)`** — replaces the builder for the common single-tag case:

```java
// Before (still works; use for multiple tags)
AppendEvent.builder(type(WalletOpened.class))
    .tag(WALLET_ID, walletId)
    .data(walletOpened)
    .build();

// After (preferred for single-tag events)
AppendEvent.of(type(WalletOpened.class), WALLET_ID, walletId, walletOpened);
```

A no-tag variant `AppendEvent.of(type, data)` is also available for events whose tag lives on the
decision model rather than the event itself.

### Migration

No migration needed. Existing call sites continue to compile without changes.

---

## See Also

- [`crablet-automations/README.md`](../../crablet-automations/README.md) — AutomationHandler reference
- [`crablet-metrics-micrometer/README.md`](../../crablet-metrics-micrometer/README.md) — Breaking changes section
- [`docs/OBSERVABILITY.md`](OBSERVABILITY.md) — Updated Grafana dashboard instructions
