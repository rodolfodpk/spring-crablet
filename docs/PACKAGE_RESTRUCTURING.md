# eventstore Package Restructuring

## Context

The `crablet-eventstore` module had 7 sub-packages but only 3 represent distinct user-facing
concepts. The `store` and `dcb` packages are inseparable from a user perspective — you always
use DCB types alongside `EventStore`. The `clock` package contains one public interface
(`ClockProvider`) that fits naturally with the core API. Meanwhile, real implementation classes
(`EventStoreImpl`, `ClockProviderImpl`, `QuerySqlBuilderImpl`, etc.) were co-located with their
public interfaces with no signal that users shouldn't touch them.

**Goal:** Merge related packages, introduce an `internal` package for implementation classes,
and reduce user import prefixes from 5 to 3.

---

## Before → After

### User-facing packages (3, down from 5)

| Before | After | Change |
|--------|-------|--------|
| `com.crablet.eventstore.store` | `com.crablet.eventstore` | merged into root |
| `com.crablet.eventstore.dcb` | `com.crablet.eventstore` | merged into root |
| `com.crablet.eventstore.clock` (ClockProvider only) | `com.crablet.eventstore` | merged into root |
| `com.crablet.eventstore.query` | `com.crablet.eventstore.query` | unchanged |
| `com.crablet.eventstore.period` | `com.crablet.eventstore.period` | unchanged |

### Implementation packages (hidden by convention)

| Before | After |
|--------|-------|
| `store.EventStoreImpl`, `store.EventStoreConfig` | `internal` |
| `clock.ClockProviderImpl` | `internal` |
| `query.QuerySqlBuilder`, `query.QuerySqlBuilderImpl`, `query.EventRepositoryImpl` | `internal` |
| `config.DataSourceConfig`, `config.ReadReplicaProperties`, `config.DataSourceConfigProperties` | `internal` |

### Packages deleted

`com.crablet.eventstore.dcb`, `com.crablet.eventstore.clock`, `com.crablet.eventstore.config`

### Unchanged

`com.crablet.eventstore.metrics` — kept separate (Spring ApplicationEvents consumed by
`crablet-metrics-micrometer`; not user API but not internal either).

---

## Type Migration

### → `com.crablet.eventstore` (root)

From `store`: `EventStore`, `AppendEvent`, `StoredEvent`, `StreamPosition`, `Tag`,
`EventStoreException`, `EventType`

From `dcb`: `AppendCondition`, `AppendConditionBuilder`, `ConcurrencyException`, `DCBViolation`

From `clock`: `ClockProvider`

### → `com.crablet.eventstore.internal` (new)

From `store`: `EventStoreImpl`, `EventStoreConfig`

From `clock`: `ClockProviderImpl`

From `query`: `QuerySqlBuilder`, `QuerySqlBuilderImpl`, `EventRepositoryImpl`

From `config`: `DataSourceConfig`, `ReadReplicaProperties`, `DataSourceConfigProperties`

### Unchanged

`com.crablet.eventstore.query`: `Query`, `QueryItem`, `QueryBuilder`, `StateProjector`,
`ProjectionResult`, `EventDeserializer`, `EventRepository`

`com.crablet.eventstore.period`: `PeriodType`, `PeriodConfig`, `PeriodTags`

`com.crablet.eventstore.metrics`: `MetricEvent`, `EventsAppendedMetric`, `EventTypeMetric`,
`ConcurrencyViolationMetric`

---

## Impact on Consumers

### Domain code (before → after)

```java
// Before
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.StreamPosition;
import com.crablet.eventstore.store.Tag;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.clock.ClockProvider;

// After
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.Tag;
import com.crablet.eventstore.AppendCondition;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.ClockProvider;
```

### App configuration code (before → after)

```java
// Before
import com.crablet.eventstore.store.EventStoreImpl;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.query.EventRepositoryImpl;

// After
import com.crablet.eventstore.internal.EventStoreImpl;
import com.crablet.eventstore.internal.EventStoreConfig;
import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.eventstore.internal.EventRepositoryImpl;
```

---

## Rationale

- **`store` → root**: Core write-model types are the primary API. Having them in a sub-package
  adds indirection with no benefit.
- **`dcb` → root**: DCB types (`AppendCondition`, `ConcurrencyException`) are always used
  alongside `EventStore.appendIf()`. They belong at the same level.
- **`clock` → root**: `ClockProvider` is a simple interface used by domain code. One interface
  does not warrant its own package.
- **`config` → internal**: Pure Spring infrastructure configuration never touched by domain code.
- **`internal` convention**: Java has no built-in package-private visibility across packages.
  The `internal` naming convention (as used by JDK, Jetbrains, etc.) clearly signals
  "framework-internal, subject to change, not for external use".
- **`metrics` unchanged**: Published as Spring ApplicationEvents and consumed by the separate
  `crablet-metrics-micrometer` module. Keeping a stable package here avoids cross-module churn.
