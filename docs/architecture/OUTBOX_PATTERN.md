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

## Leader Election

The outbox system uses **PostgreSQL advisory locks** for leader election via the `OutboxLeaderElector` component, ensuring only one instance processes each publisher at a time.

### Architecture

- **OutboxLeaderElector**: Manages advisory locks, heartbeats, and leader election state
- **OutboxProcessorImpl**: Delegates leadership decisions to elector, focuses on event processing
- **OutboxMetrics**: Tracks leadership state and failover events

The `OutboxLeaderElector` is a package-private Spring component that encapsulates all leader election logic:
- Advisory lock acquisition and release
- Heartbeat management for owned pairs
- Stale leader detection
- Lock state tracking (`isGlobalLeader`, `ownedPairs`)

This separation allows `OutboxProcessorImpl` to focus solely on event processing while the elector handles distributed coordination.

### Lock Strategies

#### GLOBAL Strategy
```sql
-- Single lock for all publishers
SELECT pg_try_advisory_lock(4856221667890123456);
```
- **One lock** across all instances
- **Winner takes all** - single instance processes all publishers
- **Simple but limited** - no horizontal scaling
- **Use case**: Simple deployments with 1-2 instances

#### PER_TOPIC_PUBLISHER Strategy  
```sql
-- Separate lock per (topic, publisher) pair
SELECT pg_try_advisory_lock(('crablet-outbox-pair-' + topic + '-' + publisher).hashCode());
```
- **Multiple locks** - one per (topic, publisher) pair
- **Distributed ownership** - different instances can own different pairs
- **Horizontal scaling** - add more instances to handle more publishers
- **Use case**: High-volume deployments with multiple instances

**Note**: Both strategies track position independently per (topic, publisher) pair in the database - only the lock granularity differs.

### Lock Acquisition Process

1. **Startup**: Each instance tries to acquire locks for configured pairs
2. **Non-blocking**: `pg_try_advisory_lock()` returns immediately (no waiting)
3. **Success**: Instance becomes leader for that pair
4. **Failure**: Another instance already owns that pair
5. **Crash Recovery**: PostgreSQL automatically releases locks when connection drops

### Example: 3 Instances, 6 Publisher Pairs

```
Instance A startup:
✓ wallet-events:KafkaPublisher     (acquired)
✓ payment-events:AnalyticsPublisher (acquired)
✗ wallet-events:WebhookPublisher   (owned by Instance B)

Instance B startup:  
✗ wallet-events:KafkaPublisher     (owned by Instance A)
✓ wallet-events:WebhookPublisher   (acquired)
✓ user-events:EmailPublisher       (acquired)

Instance C startup:
✗ payment-events:AnalyticsPublisher (owned by Instance A)
✓ audit-events:LogPublisher        (acquired)
✓ metrics-events:StatsPublisher    (acquired)
```

### Benefits

- **Automatic Failover**: If Instance A crashes, its locks are released and other instances can acquire them
- **No Split-Brain**: PostgreSQL ensures only one instance can hold a lock
- **Zero Configuration**: No external coordination service needed
- **Database Integration**: Leverages existing PostgreSQL infrastructure

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

## Deployment Architecture

The outbox processor supports different deployment strategies via lock configuration:

### Single Machine (GLOBAL)
```properties
crablet.outbox.lock-strategy=GLOBAL
```
- **One instance** handles all publishers
- **Simple deployment** - single point of processing
- **Single point of failure** - if instance fails, no publishing until restart

### Multiple Machines (PER_TOPIC_PUBLISHER)
```properties
crablet.outbox.lock-strategy=PER_TOPIC_PUBLISHER
```
- **N machines** each own specific (topic, publisher) pairs
- **Distributed processing** - each machine handles its assigned pairs
- **Fault tolerance** - if one machine fails, others continue processing
- **Horizontal scaling** - add more machines to handle more publishers

**Example with 3 machines:**
- Machine A: `wallet-events:KafkaPublisher`, `payment-events:AnalyticsPublisher`
- Machine B: `wallet-events:WebhookPublisher`, `user-events:EmailPublisher`  
- Machine C: `audit-events:LogPublisher`, `metrics-events:StatsPublisher`

## Architecture Diagrams

### Simple: Single Machine, Single Publisher
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │───▶│   Event Store    │───▶│  Outbox Topics  │
│   (DCB Commands)│    │  (events table)  │    │  (1 publisher)  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │   State Queries  │    │ External System │
                       │  (Projections)   │    │    (Kafka)      │
                       └──────────────────┘    └─────────────────┘

Configuration:
crablet.outbox.enabled=true
crablet.outbox.lock-strategy=GLOBAL
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher
```

### Medium: Single Machine, Multiple Publishers (Fan-out)
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │───▶│   Event Store    │───▶│  Outbox Topics  │
│   (DCB Commands)│    │  (events table)  │    │ (2 publishers)  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │   State Queries  │    │ External Systems│
                       │  (Projections)   │    │ Kafka + Webhooks│
                       └──────────────────┘    └─────────────────┘

Configuration:
crablet.outbox.enabled=true
crablet.outbox.lock-strategy=GLOBAL
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher,WebhookPublisher
```

### Complex: Multiple Machines, Distributed Publishers
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │───▶│   Event Store    │───▶│  Outbox Topics  │
│   (DCB Commands)│    │  (events table)  │    │ (distributed)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │   State Queries  │    │ External Systems│
                       │  (Projections)   │    │ Kafka + Webhooks│
                       └──────────────────┘    │ + Analytics     │
                                               └─────────────────┘

Machine A (wallet-events:KafkaPublisher):
┌─────────────────┐    ┌─────────────────┐
│  Outbox Machine │───▶│     Kafka       │
│       A         │    │   (wallet evts) │
└─────────────────┘    └─────────────────┘

Machine B (wallet-events:WebhookPublisher):
┌─────────────────┐    ┌─────────────────┐
│  Outbox Machine │───▶│   Webhooks      │
│       B         │    │  (wallet evts)  │
└─────────────────┘    └─────────────────┘

Machine C (payment-events:AnalyticsPublisher):
┌─────────────────┐    ┌─────────────────┐
│  Outbox Machine │───▶│   Analytics     │
│       C         │    │ (payment evts)  │
└─────────────────┘    └─────────────────┘

Configuration:
crablet.outbox.enabled=true
crablet.outbox.lock-strategy=PER_TOPIC_PUBLISHER
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher,WebhookPublisher
crablet.outbox.topics.payment-events.required-tags=payment_id
crablet.outbox.topics.payment-events.publishers=AnalyticsPublisher
```

## Performance

- **Polling Interval**: 5-30 seconds (configurable, default: 1 second)
- **Batch Size**: 100 events (configurable)
- **Isolation**: READ COMMITTED (sufficient due to DCB advisory locks)
- **Scalability**: Single machine (GLOBAL) or distributed (PER_TOPIC_PUBLISHER)

The outbox pattern adds reliable external publishing to your DCB event sourcing without compromising the core DCB guarantees.