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

### Batch Processing

The outbox processes events in batches to balance throughput and latency:

- **Default batch size**: 100 events per publisher cycle
- **Configurable**: `crablet.outbox.batch-size=N`
- **SQL LIMIT**: Each publisher fetches at most N events after its last position
- **Per-cycle limit**: If 1000 events are pending, multiple cycles will process them

Example:
- Last position: 42
- Batch size: 100
- Query: `SELECT ... WHERE position > 42 ORDER BY position LIMIT 100`
- Result: Events 43-142 are published, last position updated to 142
- Next cycle: Starts from position 142

**Tuning guidance**:
- **Smaller batches** (10-50): Lower latency, more frequent polling overhead
- **Default** (100): Balanced for most workloads
- **Larger batches** (500-1000): Higher throughput, but longer publish cycles

### Exponential Backoff

To reduce unnecessary polling during idle periods, the outbox uses exponential backoff:

- **Threshold**: After N consecutive empty polls, backoff activates (default: 3)
- **Multiplier**: Each empty poll doubles the delay (default: 2x)
- **Max duration**: Capped at configurable seconds (default: 60s)
- **Reset**: Any successful publish immediately resets to normal polling

Example with 1s polling interval:
- Polls 1-3 (empty): Normal 1s interval
- Poll 4 (empty): Skip 1 cycle → 2s effective interval
- Poll 5 (empty): Skip 3 cycles → 4s effective interval
- Poll 6 (empty): Skip 7 cycles → 8s effective interval
- Poll 9+ (empty): Capped at 60s max
- Any publish: Reset to 1s interval

**Benefits**:
- Reduces CPU usage during idle periods
- Reduces database load
- Maintains responsiveness (max 60s delay)
- Per-publisher isolation

**Configuration**:
```properties
crablet.outbox.backoff.enabled=true
crablet.outbox.backoff.threshold=3              # Empty polls before backoff starts
crablet.outbox.backoff.multiplier=2             # Exponential factor (2^n)
crablet.outbox.backoff.max-seconds=60           # Max backoff duration
```

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

### Recommended Deployment: 2 Instances

**Always run exactly 2 instances for optimal balance of performance and reliability:**

- **1 primary instance** (leader): Processes all publishers
- **1 backup instance** (follower): Ready for automatic failover
- **Purpose of backup**: High availability failover, not load sharing

**Why exactly 2 instances?**

✅ **Reliability**: Automatic failover without single point of failure  
✅ **Simplicity**: No complex quorum or coordination needed  
✅ **Resource efficiency**: Backup instance uses minimal resources when idle  
✅ **Performance**: Leader processes efficiently with dedicated resources  

**Trade-offs:**

| Aspect | 2 Instances | 3+ Instances |
|--------|-------------|--------------|
| **Leader processing** | ✅ Sufficient capacity | ⚠️ Wasted capacity (only 1 works) |
| **Failover speed** | ✅ Fast (immediate) | ✅ Also fast |
| **Complexity** | ✅ Simple | ⚠️ Unnecessarily complex |
| **Resource usage** | ✅ Efficient | ⚠️ Wasted resources |
| **Cost** | ✅ Optimal | ⚠️ Higher (unused instances) |

### Leader Election

The outbox uses **PostgreSQL advisory locks** for coordination:

- **One leader** processes all publishers across all topics
- **Automatic failover** when leader crashes (PostgreSQL releases lock)
- **At startup**: Any instance can acquire the global lock
- **Leader processes**: All (topic, publisher) pairs on all topics
- **Follower waits**: Monitors heartbeat, ready to take over
- **Zero configuration**: Leader election is transparent

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
  - Partitioning outbox_topic_progress table

**Key constraints:**
1. **Global lock**: One leader processes ALL pairs (no horizontal scaling)
2. **Thread pool**: One thread per pair (consider thread limits)
3. **Database**: Sequential monitoring queries (consider query complexity)

### Deployment Patterns

#### Single Instance (Development/Testing)
- **One instance** handles all publishers
- **Simple deployment**
- **Risk**: Single point of failure

**Use when:**
- Development/testing
- Low-volume production where downtime is acceptable
- Cost-constrained environments

#### Two Instances (Production Recommended)
- **Primary instance** (leader) processes all publishers
- **Backup instance** (follower) monitors and ready for failover
- **Automatic failover** within seconds of leader failure
- **Zero downtime**: Publishing continues seamlessly

**Use when:**
- Production deployments
- High availability required
- Standard recommendation for all deployments

**Resource allocation:**
- Leader: Full CPU/memory for processing
- Follower: Minimal resources (mostly idle monitoring)

#### Multiple Instances (>2) - NOT RECOMMENDED
- **One leader** still processes everything
- **Multiple followers** waste resources
- **No performance benefit** (leader bottleneck)
- **Higher cost** (unused instance capacity)

**Only consider when:**
- Preparing for >50 pairs (architectural redesign needed anyway)
- Specific compliance requirements for N+2 redundancy
- Testing failover scenarios

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

### High Availability: Two Instances (Recommended)
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │───▶│   Event Store    │───▶│  Outbox Topics  │
│   (DCB Commands)│    │  (events table)  │    │ (centralized)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │   State Queries  │    │ External Systems│
                       │  (Projections)   │    │ Kafka + Webhooks│
                       └──────────────────┘    │ + Analytics     │
                                               └─────────────────┘

Recommended deployment: 2 instances

┌─────────────────────────────┐         ┌─────────────────────────────┐
│  Instance A (LEADER)         │         │  Instance B (FOLLOWER)        │
│  ┌─────────────────────┐     │         │  ┌─────────────────────┐     │
│  │ Outbox Processor    │────▶▶ External│  │ Outbox Processor    │     │
│  │ Status: Active      │     │  Systems│  │ Status: Standby     │     │
│  │ Lock: Held ✓        │     │         │  │ Lock: Waiting       │     │
│  │ Publishers: All     │     │         │  │ Publishers: None    │     │
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

Failure scenario: Instance A crashes

1. PostgreSQL detects connection loss
2. PostgreSQL releases global lock automatically
3. Instance B acquires lock within seconds
4. Instance B becomes leader and starts processing
5. Zero downtime - publishing continues seamlessly

Configuration (same for both instances):
crablet.outbox.enabled=true
crablet.outbox.polling-interval-ms=1000
crablet.outbox.topics.wallet-events.required-tags=wallet_id
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher,WebhookPublisher
crablet.outbox.topics.payment-events.required-tags=payment_id
crablet.outbox.topics.payment-events.publishers=AnalyticsPublisher
```

## Performance

- **Polling Interval**: Configurable per-publisher (default: 1 second)
- **Batch Size**: 100 events per cycle (configurable via `crablet.outbox.batch-size`)
- **Isolation**: READ COMMITTED (sufficient due to DCB advisory locks)
- **Leader Processing**: One leader handles all publishers across all topics
- **Scalability**: Supports single instance or two instances with automatic failover

## Trade-offs and Limits

### Architecture Trade-offs

**✅ Benefits:**
- Simple deployment model (2 instances optimal)
- Automatic failover without external coordination
- Per-publisher isolation and independent polling
- No network overhead for coordination (local PostgreSQL locks)

**⚠️ Constraints:**
- **Global lock means no horizontal scaling**: Only one instance processes at a time
- **Leader bottleneck**: All pairs processed by single leader
- **Thread per pair**: N pairs = N threads (consider thread pool limits)
- **Connection pool**: Position updates scale with pair count

### Scalability Boundaries

| Metric | Range | Status |
|--------|-------|--------|
| **Topic/Publisher Pairs** | 1-50 | ✅ Optimal |
| **Topic/Publisher Pairs** | 50-200 | ⚠️ May need tuning |
| **Topic/Publisher Pairs** | 200+ | 🔴 Architecture redesign needed |
| **Instances** | 1 | ⚠️ No redundancy (dev only) |
| **Instances** | 2 | ✅ **Recommended** |
| **Instances** | 3+ | ⚠️ Wasted resources (not recommended) |
| **Events/second** | <1000 | ✅ Comfortable |
| **Events/second** | 1000-10000 | ⚠️ Monitor performance |
| **Events/second** | 10000+ | 🔴 May need architectural changes |

### Performance Characteristics

**Within 1-50 pairs (optimized range):**
- Monitoring queries: Sub-millisecond (index-optimized)
- Leader processing: ~1-5ms per pair per cycle
- Database load: Minimal (periodic heartbeats + position updates)
- Resource usage: ~50 threads max, low CPU when idle

**Approaching 50 pairs:**
- Monitor thread pool exhaustion
- Consider connection pool sizing
- Watch for monitoring query latency increases

**Beyond 50 pairs:**
- Architectural redesign required:
  - Batch status checks instead of per-pair queries
  - Increase thread pool size
  - Consider per-pair leader locks for horizontal scaling
  - Partition outbox_topic_progress table
  - Evaluate alternatives (e.g., dedicated event streaming infrastructure)

The outbox pattern adds reliable external publishing to your DCB event sourcing without compromising the core DCB guarantees.