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

- **GIN index on `events.tags`** — covers multi-tag containment checks (`@>`) used in the DCB
  conflict and idempotency fallback paths.
- **Composite B-tree index (type, position)** — optimized for the DCB query pattern.
- **`event_tags` derived table** — a normalized B-tree-indexed projection of `events.tags`,
  maintained atomically on every append. Replaces `unnest(tags)` array scans for the per-processor
  poller and for the common single-tag idempotency and conflict check paths.

### `event_tags` derived table

`event_tags` is the primary performance mechanism for tag-based filtering at scale. Each row
represents one `key=value` pair from one event, indexed on `(key, value, position)`. Queries
that previously scanned `unnest(events.tags)` per row now do a B-tree lookup.

**Tradeoff:** every append writes additional rows to `event_tags` (one per tag per event). Events
with many tags increase write amplification proportionally. The table is derived data — `events`
remains the canonical source of truth. If `event_tags` ever drifts from `events`, run the drift
check queries in `docs/dev/plans/eventstore-schema-performance-plan.md`.

**Multi-tag paths** (idempotency queries with more than one tag, DCB conflict checks with more
than one condition tag) fall back to the GIN index on `events.tags` to avoid the complexity of
matching all pairs across `event_tags` rows. Single-tag paths — which cover the vast majority of
real-world usage — use `event_tags` exclusively.

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
