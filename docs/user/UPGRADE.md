# Upgrade Guide

Step-by-step migration notes for each breaking change in the `1.0.0-SNAPSHOT` series. Changes are listed newest first.

Crablet uses `@Stable` as a documented compatibility signal for application-facing APIs. It is
not currently enforced by tooling. After `1.0`, breaking changes to stable APIs should be covered
here, and should use deprecation first when that is practical.

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

## Shared-fetch schema migration (V14)

**Affects:** Deployments enabling `*.shared-fetch.enabled=true`

### What changed

Shared-fetch mode requires two new tables (`module_scan_progress`, `processor_scan_progress`) added in Flyway migration `V14`.

### Migration

Shared-fetch is opt-in. If you enable it:

1. Ensure your Flyway migrations run up to V14 before deploying.
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

## See Also

- [`crablet-automations/README.md`](../../crablet-automations/README.md) — AutomationHandler reference
- [`crablet-metrics-micrometer/README.md`](../../crablet-metrics-micrometer/README.md) — Breaking changes section
- [`docs/OBSERVABILITY.md`](OBSERVABILITY.md) — Updated Grafana dashboard instructions
