# Outbox Pattern for DCB Event Sourcing

## Overview

The outbox pattern provides **reliable event publishing** for DCB-based event sourcing. It ensures events are published to external systems (Kafka, webhooks, analytics) with the same transactional guarantees as your DCB operations.

## DCB Integration

### How It Works with DCB

```java
public CommandResult handleTransfer(TransferCommand command) {
    // 1. DCB: Read current state with cursor
    ProjectionResult<WalletBalanceState> projection = 
        balanceProjector.projectWalletBalance(eventStore, command.fromWalletId(), decisionModel);
    
    // 2. Business logic - generate multiple events
    MoneyTransferred transfer = MoneyTransferred.of(/* ... */);
    DepositMade deposit = DepositMade.of(/* ... */);
    WithdrawalMade withdrawal = WithdrawalMade.of(/* ... */);
    
    // 3. DCB: Append multiple events with concurrency control
    List<AppendEvent> events = List.of(
        AppendEvent.builder("MoneyTransferred")
            .tag("from_wallet_id", command.fromWalletId())
            .tag("to_wallet_id", command.toWalletId())
            .data(serializeEvent(transfer))
            .build(),
        AppendEvent.builder("WithdrawalMade")
            .tag("wallet_id", command.fromWalletId())
            .data(serializeEvent(withdrawal))
            .build(),
        AppendEvent.builder("DepositMade")
            .tag("wallet_id", command.toWalletId())
            .data(serializeEvent(deposit))
            .build()
    );
    
    AppendCondition condition = decisionModel
        .toAppendCondition(projection.cursor())
        .build();
    
    // 4. All events stored atomically with DCB consistency
    // 5. Outbox automatically publishes all these events to external systems
    return CommandResult.of(events, condition);
}
```

**Key Point**: The events in `CommandResult` (can be 1 or N events) are stored with DCB transactional guarantees, then outbox publishes all these events reliably to external systems.

## How It Works

1. **Handler** returns `CommandResult(events, condition)`
2. **CommandExecutor** calls `EventStore.appendIf()` → stores events in `events` table
3. **Outbox processor** polls `events` table (`WHERE position > last_position`)
4. **Publishers** send events to external systems (Kafka, webhooks, etc.)
5. **Progress tracking** updates `outbox_topic_progress` with new position

## Configuration

```properties
# Enable outbox processing
crablet.outbox.enabled=true

# Polling interval (milliseconds) - adjust based on requirements
crablet.outbox.polling-interval-ms=5000

# Topic routing (matches DCB event tags)
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher

crablet.outbox.topics.payment-events.any-of-tags=payment_id,transfer_id
crablet.outbox.topics.payment-events.publishers=AnalyticsPublisher
```

## Publishers

### Available Publishers
- `LogPublisher` - Development/testing
- `TestPublisher` - Integration tests  
- `StatisticsPublisher` - Monitoring
- `GlobalStatisticsPublisher` - Always-on stats

### Custom Publishers
```java
@Component
public class KafkaPublisher implements OutboxPublisher {
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        // Publish to Kafka
    }
    
    @Override
    public String getName() {
        return "KafkaPublisher";
    }
    
    @Override
    public boolean isHealthy() {
        return kafkaTemplate.isHealthy();
    }
}
```

## Management

### REST API
```bash
# Pause/resume publishers
curl -X POST http://localhost:8080/api/outbox/publishers/KafkaPublisher/pause
curl -X POST http://localhost:8080/api/outbox/publishers/KafkaPublisher/resume

# Check publisher status
curl http://localhost:8080/api/outbox/publishers/KafkaPublisher
curl http://localhost:8080/api/outbox/publishers/lag
```

### SQL Management
```sql
-- Pause publisher
UPDATE outbox_topic_progress 
SET status = 'PAUSED' 
WHERE publisher = 'KafkaPublisher';

-- Reset failed publisher
UPDATE outbox_topic_progress 
SET status = 'ACTIVE', error_count = 0, last_error = NULL 
WHERE publisher = 'KafkaPublisher';
```

## Guarantees

- **Transactional Consistency**: Events published atomically with DCB operations
- **At-Least-Once Delivery**: Events may be published multiple times (idempotent consumers required)
- **Global Ordering**: Events published in exact position order
- **Independent Publishers**: Each publisher tracks its own progress per topic

## When to Use

✅ **Use when you need external event publishing:**
- Event-driven microservices
- Analytics and reporting
- Integration with external systems
- CQRS read model updates

❌ **Don't use for:**
- Internal-only event sourcing (DCB alone is sufficient)
- High-frequency, low-latency events
- Exactly-once delivery requirements

## Performance

- **Polling Interval**: 5-30 seconds (configurable, default: 1 second)
- **Batch Size**: 100 events (configurable)
- **Isolation**: READ COMMITTED (sufficient due to DCB advisory locks)
- **Scalability**: Multiple processors can run simultaneously

The outbox pattern adds reliable external publishing to your DCB event sourcing without compromising the core DCB guarantees.