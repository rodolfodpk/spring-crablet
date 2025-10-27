# Outbox Pattern Rationale

## The Problem: DCB + External Publishing

Your DCB event sourcing provides excellent consistency guarantees, but what about publishing events to external systems?

```java
// ❌ Problematic - not atomic with DCB
public CommandResult handleTransfer(TransferCommand command) {
    // DCB: Store multiple events atomically
    CommandResult result = transferHandler.handle(eventStore, command);
    
    // External publishing - what if this fails?
    // Events are already stored, but external publishing can fail independently
    kafkaPublisher.publish(result.events());
    webhookPublisher.notify(result.events());
}
```

**The Issue**: External systems can fail independently, breaking the transactional consistency that DCB provides.

## The Solution: Outbox Pattern

The outbox pattern extends DCB's transactional guarantees to external publishing:

```java
// ✅ Correct - atomic with DCB
public CommandResult handleTransfer(TransferCommand command) {
    // DCB: Store multiple events atomically
    CommandResult result = transferHandler.handle(eventStore, command);
    
    // Outbox: All events from CommandResult automatically published to external systems
    // (Kafka, webhooks, analytics) with same transactional guarantees
    return result;
}
```

## Key Benefits for DCB

### 1. **Transactional Consistency**
Events are published atomically with DCB operations - no dual-write problem.

### 2. **DCB Integration**
- Uses same event tags for topic routing
- Leverages DCB's position-based ordering
- Maintains DCB's concurrency control guarantees

### 3. **Independent Publishers**
Each publisher tracks its own progress independently. Multiple publishers per topic enable fan-out scenarios (e.g., same events to Kafka AND analytics), but one publisher per topic is typical.

### 4. **Operational Control**
- Pause/resume publishers independently
- Reset failed publishers without data loss
- Monitor publisher lag and health
- Deploy as single machine (GLOBAL) or distributed (PER_TOPIC_PUBLISHER)

## When to Use

### ✅ **Use Outbox When:**
- Publishing to external systems (Kafka, webhooks, analytics)
- Event-driven microservices architecture
- CQRS read model updates
- Integration with external APIs

### ❌ **Don't Use Outbox When:**
- Internal-only event sourcing (DCB alone is sufficient)
- High-frequency, low-latency requirements (polling adds 5-30s latency)
- Exactly-once delivery requirements (provides at-least-once only)
- Simple CRUD applications (overhead not justified)

## Alternatives Comparison

### Synchronous Publishing
- ❌ Breaks transaction atomicity with DCB operations
- ❌ External failures affect command processing (retry complexity)
- ✅ Lower latency (immediate publishing)

### Change Data Capture (CDC)
- ❌ Requires database-level configuration (debezium, wal2json)
- ❌ Less control over publishing logic and filtering
- ✅ No application-level polling overhead
- ⚠️ More complex deployment and monitoring

### Outbox Pattern (This Library)
- ✅ Transactional consistency with DCB operations
- ✅ Full control over publishers and routing logic
- ✅ PostgreSQL advisory locks (no external coordination needed)
- ✅ Independent publisher progress tracking
- ⚠️ Polling adds latency (5-30 seconds configurable)

## Implementation

For complete implementation details, see:
- **[Outbox Pattern](OUTBOX_PATTERN.md)** - Architecture, configuration, deployment, and troubleshooting
- **[Outbox Metrics](OUTBOX_METRICS.md)** - Monitoring and observability guide