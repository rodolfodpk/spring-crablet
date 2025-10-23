# Outbox Pattern Rationale

## The Problem: DCB + External Publishing

Your DCB event sourcing provides excellent consistency guarantees, but what about publishing events to external systems?

```java
// ❌ Problematic - not atomic with DCB
public CommandResult handleTransfer(TransferCommand command) {
    // DCB: Store multiple events atomically
    CommandResult result = transferHandler.handle(eventStore, command);
    
    // External publishing - what if this fails?
    kafkaPublisher.publish(new MoneyTransferredEvent(command));
    kafkaPublisher.publish(new WithdrawalMadeEvent(command));
    kafkaPublisher.publish(new DepositMadeEvent(command));
    webhookPublisher.notify(command);
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

## How It Works

1. **DCB stores events** with full transactional consistency
2. **Outbox processor polls** events after each publisher's last position
3. **Publishers send** events to external systems (Kafka, webhooks, etc.)
4. **Progress tracking** ensures no events are lost or duplicated

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

## When to Use

### ✅ **Use Outbox When:**
- Publishing to external systems (Kafka, webhooks, analytics)
- Event-driven microservices architecture
- CQRS read model updates
- Integration with external APIs

### ❌ **Don't Use Outbox When:**
- Internal-only event sourcing (DCB alone is sufficient)
- High-frequency, low-latency requirements
- Exactly-once delivery requirements
- Simple CRUD applications

## DCB + Outbox Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   DCB Command   │───▶│   Event Store    │───▶│  Outbox Topics  │
│   Processing    │    │  (Transactional) │    │  (Publishers)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │   State Queries  │    │ External Systems│
                       │  (Projections)   │    │ (Kafka, Webhooks│
                       └──────────────────┘    │  Analytics)     │
                                               └─────────────────┘
```

## Performance Characteristics

- **DCB Operations**: ~350 req/s (cursor-only checks)
- **Outbox Publishing**: 1-second polling interval
- **Batch Processing**: 100 events per batch
- **Scalability**: Multiple processors can run simultaneously

## Conclusion

The outbox pattern extends DCB's transactional guarantees to external systems, providing reliable event publishing without compromising DCB's consistency model. It's an optional add-on that enhances DCB-based event sourcing for event-driven architectures.