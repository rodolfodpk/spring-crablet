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

This separation allows `OutboxProcessorImpl` to focus solely on event processing and per-publisher scheduling while the elector handles distributed coordination.

### Leader Election

#### Global Lock Strategy
```sql
-- Single advisory lock for all publishers
SELECT pg_try_advisory_lock(4856221667890123456);
```
- **One leader** processes all publishers across all topics
- **Automatic failover**: If leader crashes, locks are released and another instance takes over
- **No configuration needed**: Leader election is transparent
- **Position tracking**: Each (topic, publisher) pair tracks its progress independently

### Per-Publisher Schedulers

Each (topic, publisher) pair has its own independent scheduler, providing:

- **Isolation**: One publisher failure doesn't affect others
- **Flexible polling**: Configure per-publisher polling intervals
- **Clear boundaries**: Each scheduler polls independently

### Lock Acquisition Process

1. **Startup**: Each instance tries to acquire the global lock
2. **Non-blocking**: `pg_try_advisory_lock()` returns immediately (no waiting)
3. **Success**: Instance becomes leader and processes all publishers
4. **Failure**: Another instance is already the leader
5. **Crash Recovery**: PostgreSQL automatically releases locks when connection drops

### Example: 3 Instances

```
Instance A startup:
✓ Global lock acquired → becomes leader
  - Processes wallet-events:KafkaPublisher
  - Processes wallet-events:WebhookPublisher  
  - Processes payment-events:AnalyticsPublisher

Instance B startup:
✗ Global lock already held by Instance A → follower

Instance C startup:
✗ Global lock already held by Instance A → follower

Instance A crashes:
✓ PostgreSQL releases lock
✓ Instance B or C automatically becomes new leader
```

### Benefits

- **Automatic Failover**: If leader crashes, locks are automatically released
- **No Split-Brain**: PostgreSQL ensures only one instance can hold the lock
- **Zero Configuration**: No external coordination service needed
- **Database Integration**: Leverages existing PostgreSQL infrastructure
- **Simple Architecture**: One lock strategy, easy to understand and debug

## Publishers

### Available Publishers
- `LogPublisher` - Development/testing
- `CountDownLatchPublisher` - Integration tests (test-scope only)
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

The outbox uses a **global leader lock** for coordination:

- **One leader** processes all publishers across all topics
- **Automatic failover** when leader crashes
- **Support for multiple instances** with automatic failover
- **Each (topic, publisher) pair** has an independent scheduler with configurable polling intervals

### Single Machine Deployment
- **One instance** handles all publishers
- **Simple deployment**
- **Backup instance** automatically takes over if primary crashes

### Multiple Machine Deployment
- **N machines**: one leader, others as backups
- **Automatic failover**: if leader crashes, another instance becomes leader
- **Zero downtime**: publishing continues seamlessly after failover

## Architecture Diagrams

### Simple: Single Instance, Multiple Publishers
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
crablet.outbox.polling-interval-ms=1000
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher
```

### Fan-out: Single Topic, Multiple Publishers
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
crablet.outbox.polling-interval-ms=1000
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher,WebhookPublisher
```

### High Availability: Multiple Instances with Failover
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

Multiple instances (e.g., in Kubernetes):

Instance A (leader):
┌─────────────────┐    ┌─────────────────┐
│  Outbox (leader)│───▶│  External Systems│
│     Machine A   │    │  Kafka, Webhooks│
└─────────────────┘    └─────────────────┘

Instance B (backup):
┌─────────────────┐
│  Outbox (follower)
│     Machine B
└─────────────────┘

Instance C (backup):
┌─────────────────┐
│  Outbox (follower)
│     Machine C
└─────────────────┘

If Instance A crashes:
✓ PostgreSQL releases global lock
✓ Instance B or C becomes new leader automatically
✓ Publishing continues seamlessly

Configuration (same for all instances):
crablet.outbox.enabled=true
crablet.outbox.polling-interval-ms=1000
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher,WebhookPublisher
crablet.outbox.topics.payment-events.required-tags=payment_id
crablet.outbox.topics.payment-events.publishers=AnalyticsPublisher
```

## Performance

- **Polling Interval**: Configurable per-publisher (default: 1 second)
- **Batch Size**: 100 events (configurable)
- **Isolation**: READ COMMITTED (sufficient due to DCB advisory locks)
- **Scalability**: Supports single instance or multiple instances with automatic failover

The outbox pattern adds reliable external publishing to your DCB event sourcing without compromising the core DCB guarantees.