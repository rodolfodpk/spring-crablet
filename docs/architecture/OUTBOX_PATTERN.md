# Outbox Pattern for DCB Event Sourcing

## Overview

The outbox pattern provides **reliable event publishing** for DCB-based event sourcing. It ensures events are published to external systems (Kafka, webhooks, analytics) with the same transactional guarantees as your DCB operations.

## DCB Integration

### How It Works with DCB

```java
@Transactional
public CommandResult handleDeposit(DepositCommand command) {
    // 1. DCB: Read current state with cursor
    ProjectionResult<WalletBalanceState> projection = 
        balanceProjector.projectWalletBalance(eventStore, command.walletId(), decisionModel);
    
    // 2. Business logic
    DepositMade deposit = DepositMade.of(/* ... */);
    
    // 3. DCB: Append event with concurrency control
    AppendEvent event = AppendEvent.builder("DepositMade")
        .tag("wallet_id", command.walletId())
        .data(serializeEvent(deposit))
        .build();
    
    AppendCondition condition = decisionModel
        .toAppendCondition(projection.cursor())
        .build();
    
    // 4. Event stored atomically with DCB consistency
    // 5. Outbox automatically publishes to external systems
    return CommandResult.of(List.of(event), condition);
}
```

**Key Point**: Events are stored with DCB transactional guarantees, then outbox publishes them reliably to external systems.

## Configuration

```properties
# Enable outbox processing
crablet.outbox.enabled=true

# Topic routing (matches DCB event tags)
crablet.outbox.topics.wallet-events.required-tags=walletId
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher,WebhookPublisher

crablet.outbox.topics.payment-events.any-of-tags=paymentId,transferId
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
UPDATE outbox_publisher_progress 
SET status = 'PAUSED' 
WHERE publisher_name = 'KafkaPublisher';

-- Reset failed publisher
UPDATE outbox_publisher_progress 
SET status = 'ACTIVE', error_count = 0, last_error = NULL 
WHERE publisher_name = 'KafkaPublisher';
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

- **Polling Interval**: 1 second (configurable)
- **Batch Size**: 100 events (configurable)
- **Isolation**: READ COMMITTED (sufficient due to DCB advisory locks)
- **Scalability**: Multiple processors can run simultaneously

The outbox pattern adds reliable external publishing to your DCB event sourcing without compromising the core DCB guarantees.