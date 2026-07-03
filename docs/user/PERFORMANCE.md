# Performance Considerations

## Read Replicas (Optional)

Configure separate read and write datasources for horizontal scaling:

```java
@Bean
public EventStore eventStore(
    WriteDataSource writeDataSource,
    ReadDataSource readDataSource,
    // ...
) {
    return new EventStoreImpl(writeDataSource.dataSource(), readDataSource.dataSource(), ...);
}
```

**Benefits:**
- Event fetching (views, outbox) uses read replicas
- Command appends go to primary
- Reduces load on primary database
- Leader election and other session-scoped features must remain on `WriteDataSource`

See `crablet-eventstore/docs/READ_REPLICAS.md` for full configuration.

## Database Indexes

The framework relies on several PostgreSQL indexes for performance:

- **GIN index on `crablet_events.tags`** — covers multi-tag containment checks (`@>`) used in the DCB
  conflict and idempotency fallback paths.
- **Composite B-tree index (type, position)** — optimized for the DCB query pattern.
- **`crablet_event_tags` derived table** — a normalized B-tree-indexed projection of `crablet_events.tags`,
  maintained atomically on every append. Used by the per-processor poller to replace
  `unnest(tags)` array scans with indexed EXISTS subqueries. The primary key
  `(key, value, position)` serves exact tag filters; `idx_crablet_event_tags_key_position`
  serves broad key-existence filters such as view processors that consume all events
  carrying `wallet_id`.

### `crablet_event_tags` derived table

`crablet_event_tags` is the performance mechanism for poller tag filtering at scale. Each row represents
one `key=value` pair from one event. Per-processor poller queries (`EventSelectionWhereClauseBuilder`)
use correlated EXISTS subqueries against `crablet_event_tags` instead of scanning `unnest(crablet_events.tags)`
per row.

The table has two important lookup shapes:

- exact tag filters use `(key, value, position)`, for example `wallet_id=w1`
- key-existence filters use `(key, position)`, for example all events that carry any `wallet_id`

The second shape matters for broad projections and outbox topics that process all events in a
business category, not just one entity instance.

**Idempotency and DCB conflict checks** inside `append_events_if` continue to use the GIN index
on `crablet_events.tags`. Real decision models use 2+ tags per criterion (e.g. `wallet_id + year + month`
in period-aware handlers), so the GIN `@>` path handles the common case directly and uniformly.

**Tradeoff:** every append writes one `crablet_event_tags` row per tag per event (write amplification).
The table is derived data — `crablet_events` remains the canonical source of truth. If `crablet_event_tags` ever
drifts from `crablet_events`, run the drift check queries in `docs/dev/plans/eventstore-schema-performance-plan.md`.

## Batch Processing

**Views and Outbox:**
- Default batch size: 100 events
- Configurable via application properties
- Larger batches = better throughput, higher latency
- Smaller batches = lower latency, more database round-trips

**Event Appending:**
- Uses `UNNEST` for batch inserts (single database round-trip)
- `append_events_if()` function handles multiple events atomically

## Connection Pooling

Consider PgBouncer for connection pooling in production:
- Reduces connection overhead
- Handles connection limits gracefully
- Transaction-level pooling recommended

See `crablet-eventstore/docs/CONNECTION_POOLERS.md` for PgBouncer/PgCat guidance and the LISTEN/NOTIFY constraint on pooler URLs.

## Closing the Books Pattern

For entities with long event histories (millions of events):
- Use `@PeriodConfig` to segment events by period (monthly, daily, etc.)
- Query only current period events instead of full history
- Significant performance improvement for mature entities

See `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`.

## Automation Pattern (Separation of Concerns)

- Command handlers record facts
- Views model current decision state
- Automations react asynchronously and own follow-up application behavior
- Outbox publishes stored events to external systems

If stored events need to be published outside the application boundary, use an `OutboxPublisher`. If an event should trigger application behavior, implement that behavior by returning `AutomationDecision.ExecuteCommand` from `AutomationHandler.decide()` and use commands/events to record the outcome.
