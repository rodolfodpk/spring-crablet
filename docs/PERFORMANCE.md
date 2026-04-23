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

The framework relies heavily on PostgreSQL indexes for performance:

- **GIN index on tags** — fast tag-based filtering (`WHERE tags @> '{wallet_id:alice}'`)
- **Composite index (type, position)** — optimized for DCB query pattern
- **Composite GIN index (type, tags)** — optimized for idempotency checks

Tag-based filtering is O(log n) with GIN indexes, making it efficient even with millions of events.

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

If stored events need to be sent to an external HTTP API, Kafka, analytics, or CRM system, use an `OutboxPublisher`. If an event should trigger application behavior, implement that behavior by returning `AutomationDecision.ExecuteCommand` from `AutomationHandler.decide()` and use commands/events to record the outcome.
