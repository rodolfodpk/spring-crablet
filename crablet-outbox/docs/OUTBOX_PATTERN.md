# Outbox Pattern for DCB Event Sourcing

## Quick Reference

**Outbox pattern in 60 seconds:**

The outbox provides **reliable event publishing** for DCB-based event sourcing. It ensures stored events are handed to configured `OutboxPublisher` implementations without creating a dual-write path in command handlers.

**How it works:**
1. Handler returns `CommandDecision` (Commutative / NonCommutative / Idempotent)
2. `CommandExecutor` calls the appropriate `EventStore` append method → stores events in `crablet_events` table
3. Outbox processor polls transaction-safe events (`WHERE position > last_position` plus PostgreSQL's transaction safe horizon)
4. Publishers send events to external systems
5. Progress tracking updates `crablet_outbox_topic_progress` with new position

**Key Features:**
- ✅ At-least-once delivery semantics
- ✅ Leader election for high availability (2 instances recommended)
- ✅ Per-publisher progress tracking
- ✅ Automatic failover (5-30 seconds)

📖 **Details:** See [sections below](#dcb-integration).

## Overview

The outbox pattern provides **reliable event publishing** for DCB-based event sourcing. It ensures stored events are handed to configured `OutboxPublisher` implementations without creating a dual-write path in command handlers.

## DCB Integration

### How It Works with DCB

```java
// Inside NonCommutativeCommandHandler<TransferCommand>.decide():
public CommandDecision.NonCommutative decide(EventStore eventStore, TransferCommand command) {
    // 1. DCB: Read current state with stream position
    Query decisionModel = WalletQueryPatterns.transferDecisionModel(
        command.fromWalletId(), command.toWalletId());
    ProjectionResult<TransferState> projection = eventStore.project(decisionModel, projector);
    
    // 2. Business logic - generate event(s)
    MoneyTransferred transfer = MoneyTransferred.of(/* ... */);
    
    // 3. DCB: Return decision — CommandExecutor appends with concurrency control
    AppendEvent event = AppendEvent.builder("MoneyTransferred")
        .tag("from_wallet_id", command.fromWalletId())
        .tag("to_wallet_id", command.toWalletId())
        .data(transfer)
        .build();
    
    // 4. All events stored atomically; outbox publishers handle them asynchronously
    return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
}
```

**Key Point**: The events in the `CommandDecision` (can be 1 or N events) are stored with DCB transactional guarantees, then configured outbox publishers handle them asynchronously.

## How It Works

1. **Handler** returns `CommandDecision` (Commutative / NonCommutative / Idempotent)
2. **CommandExecutor** calls the appropriate EventStore append method → stores events in `crablet_events` table
3. **Outbox processor** polls transaction-safe events after `last_position`
4. **Publishers** receive matching stored events
5. **Progress tracking** updates `crablet_outbox_topic_progress` with new position

### Ordering and Transaction Safety

Stored events carry PostgreSQL's `transaction_id`. The outbox keeps its operational cursor as
`crablet_outbox_topic_progress.last_position`, but it only fetches events below PostgreSQL's safe snapshot
horizon:

```sql
transaction_id < pg_snapshot_xmin(pg_current_snapshot())
```

This is the transaction-id guard from the PostgreSQL outbox ordering pattern. It prevents the outbox
from publishing a later committed event and advancing `last_position` while an earlier transaction
can still commit and reveal a lower-position event. The cost is head-of-line waiting behind
long-running write transactions; that is preferred over skipping events.

## Configuration

```properties
# Enable outbox processing
crablet.outbox.enabled=true

# Polling interval (milliseconds)
crablet.outbox.polling-interval-ms=5000

# Leader election retry interval (milliseconds)
crablet.outbox.leader-election-retry-interval-ms=30000

# Topic routing (matches DCB event types and tags)
crablet.outbox.topics.wallet-events.event-types=WalletOpened,DepositMade
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=LogPublisher

crablet.outbox.topics.payment-events.any-of-tags=payment_id,transfer_id
crablet.outbox.topics.payment-events.publishers=StatisticsPublisher
```

Outbox topics use the shared poller [Event Selection](../../crablet-event-poller/README.md#event-selection) model: event types and tag filters combine with AND, and empty event types mean all event types. Legacy mode applies the filter in SQL per topic/publisher; shared-fetch mode applies the same selection during in-memory routing.

## Leader Election

The outbox uses **PostgreSQL advisory locks** for leader election, ensuring only one instance processes each publisher at a time.

### Overview

- **Global lock strategy**: Single advisory lock for all publishers across all topics
- **One leader**: Processes all publishers across all topics
- **Automatic failover**: If leader crashes, locks are released and another instance takes over
- **No configuration needed**: Leader election is transparent
- **Position tracking**: Each (topic, publisher) pair tracks its own progress independently

For complete leader election documentation including crash detection, failover timing, and deployment recommendations, see [Leader Election Guide](../../docs/user/LEADER_ELECTION.md).

### Per-Publisher Schedulers

Each (topic, publisher) pair has its own independent scheduler, providing:
- **Isolation**: One publisher failure doesn't affect others
- **Flexible polling**: Configure per-publisher polling intervals
- **Clear boundaries**: Each scheduler polls independently

### Batch Processing

The outbox processes events in batches to balance throughput and latency:
- **Default batch size**: 100 events per publisher cycle
- **Configurable**: `crablet.outbox.batch-size=N`
- **SQL LIMIT**: Each publisher fetches at most N events after its last position

**Tuning guidance:**
- **Smaller batches** (10-50): Lower latency, more frequent polling overhead
- **Default** (100): Balanced for most workloads
- **Larger batches** (500-1000): Higher throughput, but longer publish cycles

### Exponential Backoff

To reduce unnecessary polling during idle periods, the outbox uses exponential backoff:
- **Threshold**: After N consecutive empty polls, backoff activates (default: 3)
- **Multiplier**: Each empty poll doubles the delay (default: 2x)
- **Max duration**: Capped at configurable seconds (default: 60s)
- **Reset**: Any successful publish immediately resets to normal polling

**Configuration:**
```properties
crablet.outbox.backoff.enabled=true
crablet.outbox.backoff.threshold=3
crablet.outbox.backoff.multiplier=2
crablet.outbox.backoff.max-seconds=60
```

## Publishers

### Available Publishers
- `LogPublisher` - Development/testing
- `StatisticsPublisher` - Monitoring
- `GlobalStatisticsPublisher` - Always-on stats

### Custom Publishers

```java
@Component
public class ExamplePublisher implements OutboxPublisher {
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        // Publish events to your destination.
    }
    
    @Override
    public String getName() {
        return "ExamplePublisher";
    }
    
    @Override
    public boolean isHealthy() {
        return true;
    }
}
```

### Individual Publish Mode

`INDIVIDUAL` mode calls `publishBatch(List.of(event))` once per event while keeping the outbox progress, retry, and circuit-breaker boundary on the publisher.

## Management

### REST API
```bash
# Pause/resume publishers
curl -X POST http://localhost:8080/api/outbox/default/publishers/LogPublisher/pause
curl -X POST http://localhost:8080/api/outbox/default/publishers/LogPublisher/resume

# Check publisher status
curl http://localhost:8080/api/outbox/default/publishers/LogPublisher/status
curl http://localhost:8080/api/outbox/default/publishers/LogPublisher/lag
```

### SQL Management
```sql
-- Pause publisher
UPDATE crablet_outbox_topic_progress 
SET status = 'PAUSED' 
WHERE publisher = 'LogPublisher';

-- Reset failed publisher
UPDATE crablet_outbox_topic_progress 
SET status = 'ACTIVE', error_count = 0, last_error = NULL 
WHERE publisher = 'LogPublisher';
```

## Guarantees

### At-Least-Once Delivery

**The outbox provides At-Least-Once delivery semantics, not Exactly-Once.**

Events may be published multiple times in the following scenarios:

1. **Leader failover during publishing**: Leader crashes mid-batch, backup leader starts from last committed position
2. **Publisher failure with retry**: Publisher throws exception, position not updated, next cycle retries
3. **Transactional boundary**: Events written to `crablet_events` (COMMIT), publisher attempted but network failure

**Consumer requirements:**
- ✅ **Must be idempotent:** Handle duplicate events gracefully
- ✅ **Must track processed events:** Use event position/ID for deduplication
- ✅ **Must handle out-of-order scenarios:** Events may arrive out of sequence

**Implementation note:** The outbox updates `last_position` only after successful publish. If publishing fails, the same events will be retried on the next cycle until successful.

### Other Guarantees

- **Transactional Consistency**: Events published atomically with DCB operations
- **Position Ordering with Safe Horizon**: Events are published in position order within each publisher, after filtering out transactions that are not yet safe to poll
- **Independent Publishers**: Each publisher tracks its own progress per topic
- **Automatic Failover**: Publishing continues seamlessly if leader crashes

## When to Use

✅ **Use when you need external event publishing:**
- Event-driven microservices
- Integration with external systems
- CQRS read model updates

❌ **Don't use for:**
- Internal-only event sourcing (DCB alone is sufficient)
- High-frequency, low-latency events (polling adds latency)
- Exactly-once delivery requirements (provides at-least-once only)
- Time-critical events requiring guaranteed delivery (use synchronous publishing instead)

## Deployment Architecture

### Recommended Deployment

**Single Instance (1 replica):**
- Works fine in Kubernetes (auto-restart on crash)
- Brief downtime during pod restart (typically a few seconds to a minute)
- Suitable for development, staging, or production with acceptable restart downtime

**Multi-Instance (2+ replicas) - Recommended for Production:**
- **1 primary instance** (leader): Processes all publishers
- **1+ backup instances** (followers): Ready for automatic failover
- **Purpose of backup**: Zero-downtime failover, not load sharing
- **Failover time**: 5-30 seconds (follower takes over immediately)

**Why 2+ instances for production?**

✅ **Zero-downtime failover**: Follower takes over within 5-30 seconds  
✅ **Reliability**: No single point of failure  
✅ **Simplicity**: No complex quorum or coordination needed  
✅ **Resource efficiency**: Backup instances use minimal resources when idle

**Trade-offs:**

| Aspect | 1 Instance | 2 Instances | 3+ Instances |
|--------|------------|-------------|--------------|
| **Failover downtime** | ⚠️ Brief (pod restart) | ✅ Zero (5-30s) | ✅ Zero (5-30s) |
| **Complexity** | ✅ Simplest | ✅ Simple | ⚠️ Unnecessarily complex |
| **Resource usage** | ✅ Most efficient | ✅ Efficient | ⚠️ Wasted resources |
| **Cost** | ✅ Lowest | ✅ Optimal | ⚠️ Higher (unused instances) |

### Scaling Limits

**Optimized for:** 1-50 topic/publisher pairs

**Within this range:**
- ✅ Per-pair schedulers efficient (~50 threads)
- ✅ Global leader lock sufficient
- ✅ Database connection pool comfortable
- ✅ Monitoring queries fast

**Beyond 50 pairs:**
- ⚠️ Thread pool saturation risk
- ⚠️ Connection pool pressure
- ⚠️ Architectural changes needed:
  - Batch status checking
  - Increased thread pool size
  - Per-pair leader locks for horizontal scaling
  - Partitioning crablet_outbox_topic_progress table

**Key constraints:**
1. **Global lock**: One leader processes ALL pairs (no horizontal scaling)
2. **Thread pool**: One thread per pair (consider thread limits)
3. **Database**: Sequential monitoring queries (consider query complexity)

### Architecture Diagram

**High Availability: Two Instances (Recommended)**

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │───▶│   Event Store    │───▶│  Outbox Topics  │
│   (DCB Commands)│    │(crablet_events)  │    │ (centralized)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │                        │
                              ▼                        ▼
                     ┌──────────────────┐    ┌─────────────────┐
                     │   State Queries  │    │ Configured      │
                     │  (Projections)   │    │ Publishers      │
                     └──────────────────┘    │                 │
                                          └─────────────────┘

┌─────────────────────────────┐         ┌─────────────────────────────┐
│  Instance A (LEADER)         │         │  Instance B (FOLLOWER)        │
│  ┌─────────────────────┐     │         │  ┌─────────────────────┐     │
│  │ Outbox Processor    │────▶▶ External│  │ Outbox Processor    │     │
│  │ Status: Active      │     │  Systems│  │ Status: Standby     │     │
│  │ Lock: Held ✓        │     │         │  │ Lock: Waiting       │     │
│  └─────────────────────┘     │         │  └─────────────────────┘     │
│             ↓                │         │             ↓                │
│       Global Lock            │         │       Monitors Lock          │
│        (PostgreSQL)          │         │       (PostgreSQL)           │
└─────────────────────────────┘         └─────────────────────────────┘
              ↓                                      ↓
       ┌─────────────────────────────────────────────┐
       │      PostgreSQL (advisory locks)             │
       │  - Leader election                          │
       │  - Automatic failover                       │
       └─────────────────────────────────────────────┘
```

**Failure scenario:** Instance A crashes → PostgreSQL releases lock → Instance B detects and takes over within 5-30 seconds.

For complete details on crash detection, failover mechanism, and deployment patterns, see [Leader Election Guide](../../docs/user/LEADER_ELECTION.md).

## Performance

- **Polling Interval**: Configurable per-publisher (default: 1 second)
- **Batch Size**: 100 events per cycle (configurable via `crablet.outbox.batch-size`)
- **Isolation**: READ COMMITTED (sufficient due to DCB advisory locks)
- **Leader Processing**: One leader handles all publishers across all topics
- **Scalability**: Supports single instance or two instances with automatic failover

## Troubleshooting

### High Publisher Lag

**Symptoms:** Events not publishing, `outbox_lag` metric increasing

**Causes:**
- Leader instance down or unhealthy
- Publisher paused or in FAILED state
- Database connectivity issues
- Publisher destination unavailable

**Resolution:**
```sql
-- Check publisher status
SELECT topic, publisher, status, last_position, error_count, last_error
FROM crablet_outbox_topic_progress;

-- Resume paused publisher
UPDATE crablet_outbox_topic_progress 
SET status = 'ACTIVE' 
WHERE publisher = 'LogPublisher';

-- Reset failed publisher
UPDATE crablet_outbox_topic_progress 
SET status = 'ACTIVE', error_count = 0, last_error = NULL 
WHERE publisher = 'LogPublisher';
```

### Frequent Leader Changes

**Symptoms:** `processor_is_leader{processor="outbox"}` metric changing frequently

**Causes:**
- Network instability between instances and database
- Database connection pool exhaustion
- Instance resource constraints (CPU, memory)

**Resolution:**
- Monitor instance health metrics
- Check network latency to database
- Increase connection pool size if needed
- Verify instance resources (CPU < 80%, memory sufficient)

### Events Published Multiple Times

**Symptoms:** Duplicate events in external systems

**Expected behavior:** At-least-once delivery semantics

**Resolution:**
- This is normal behavior (see Guarantees section)
- Ensure consumers are idempotent
- Use event position/ID for deduplication

### No Events Being Published

**Symptoms:** `outbox_events_published_total` not increasing, lag growing

**Causes:**
- All instances are followers (no leader)
- Outbox disabled in configuration
- No publishers configured for topics
- All publishers paused or failed

**Resolution:**
```bash
# Check if any instance is leader
curl http://localhost:8080/actuator/metrics/outbox.is.leader

# Check configuration
curl http://localhost:8080/actuator/env | grep outbox.enabled

# Check publishers
curl http://localhost:8080/api/outbox/status
```

## See Also

- [Leader Election Guide](../../docs/user/LEADER_ELECTION.md) - Complete leader election documentation
- [EventStore README](../crablet-eventstore/README.md) - DCB event sourcing library
- [Command README](../crablet-commands/README.md) - Command framework
