# Leader Election in Crablet

Crablet uses **PostgreSQL advisory locks** for distributed leader election, ensuring only one instance processes events at a time across multiple application instances. This provides automatic failover without external coordination services.

## Overview

Leader election is used by:
- **crablet-outbox**: Ensures only one instance publishes events to external systems
- **crablet-views**: Ensures only one instance projects events into materialized views

Both modules use the generic event processor infrastructure (`crablet-event-processor`) which provides the leader election mechanism.

## How It Works

### PostgreSQL Advisory Locks

Crablet uses PostgreSQL's `pg_try_advisory_lock()` function for non-blocking leader election:

```sql
-- Attempt to acquire advisory lock (non-blocking)
SELECT pg_try_advisory_lock(4856221667890123456);
-- Returns: true if lock acquired, false if another instance holds it
```

**Key Properties:**
- **Non-blocking**: `pg_try_advisory_lock()` returns immediately (doesn't wait)
- **Session-scoped**: Lock is held for the duration of the database connection
- **Automatic release**: Lock is automatically released when the connection closes
- **Unique per key**: Different modules use different lock keys (outbox vs views)

### Leader Acquisition Process

1. **On Startup**: Each instance attempts to acquire the advisory lock
   ```java
   leaderElector.tryAcquireGlobalLeader();
   ```

2. **Lock Attempt**: Uses `pg_try_advisory_lock()` with a module-specific key:
   - **Outbox**: Lock key `4856221667890123456`
   - **Views**: Lock key `4856221667890123457`

3. **Result**:
   - If lock acquired → Instance becomes leader, starts processing
   - If lock not acquired → Instance becomes follower, waits for retry

4. **Leader Processing**: Only the leader instance:
   - Polls events from the event store
   - Processes events (publishes to external systems or projects to views)
   - Updates progress tracking

## Crash Detection and Failover

### How Crashes Are Detected

When a leader instance crashes:

1. **PostgreSQL Automatic Lock Release**: 
   - PostgreSQL detects the connection loss
   - Automatically releases all advisory locks held by that connection
   - This happens immediately when the connection is closed

2. **No Heartbeat Required**: 
   - Unlike some leader election mechanisms, Crablet doesn't require heartbeats
   - Lock release is automatic and immediate when the connection drops

### How New Leaders Are Elected

Follower instances use **two retry mechanisms** to detect leader crashes:

#### 1. Dedicated Retry Scheduler

A dedicated scheduler runs independently of processor schedulers:

```java
// Runs every 30 seconds (default, hardcoded)
leaderRetryScheduler = taskScheduler.scheduleAtFixedRate(
    this::leaderRetryTask,
    Duration.ofMillis(30000)
);
```

- **Interval**: 30 seconds (default, currently hardcoded)
- **Purpose**: Periodic check for leader availability
- **Behavior**: If not leader, attempts to acquire lock

#### 2. Processor Scheduler Retry with Cooldown

Each processor scheduler also retries when not leader:

```java
// Cooldown: 5 seconds (hardcoded)
private static final long LEADER_RETRY_COOLDOWN_MS = 5000;
```

- **Cooldown**: 5 seconds between retry attempts
- **Purpose**: Prevents multiple schedulers from simultaneously attempting lock acquisition
- **Behavior**: Only retries if cooldown period has elapsed

### Failover Timing

**Typical failover time: 5-30 seconds**

- **Lock release**: Immediate (PostgreSQL detects connection drop)
- **Detection**: Multiple retry points:
  - Dedicated scheduler: Within 30 seconds (default interval)
  - Processor schedulers: Within 5 seconds (cooldown period) when they run
- **Actual failover**: Depends on which scheduler detects first

**Example Timeline:**
```
T0: Leader instance crashes (connection closes)
T0: PostgreSQL releases advisory lock immediately
T5: Processor scheduler runs, detects no leader, acquires lock
T5: New leader starts processing immediately
```

Or if processor scheduler just ran:
```
T0: Leader instance crashes
T0: PostgreSQL releases lock
T30: Dedicated retry scheduler runs, acquires lock
T30: New leader starts processing
```

## Lock Keys

Each module uses a unique advisory lock key to prevent conflicts:

| Module | Lock Key | Purpose |
|--------|----------|---------|
| **Outbox** | `4856221667890123456` | Global lock for all outbox publishers |
| **Views** | `4856221667890123457` | Global lock for all view projections |

**Note**: Different lock keys allow the same instance to be leader for both outbox and views simultaneously, or different instances can be leaders for different modules.

## Configuration

Currently, retry intervals and cooldown periods are **hardcoded** in the implementation:

- **Dedicated retry interval**: 30 seconds (in `EventProcessorImpl.initializeSchedulers()`)
- **Cooldown period**: 5 seconds (in `EventProcessorImpl.LEADER_RETRY_COOLDOWN_MS`)

These values provide a good balance between failover speed and database load. For faster failover, you could modify the code to make these configurable, but this would increase database load from more frequent lock attempts.

## Deployment Recommendations

### Single Instance Deployment

**1 instance is sufficient** when running in an orchestration system like Kubernetes:

- **Kubernetes auto-restart**: If the pod crashes, Kubernetes automatically restarts it
- **Leader reacquisition**: When the pod restarts, it will automatically acquire the leader lock
- **Trade-off**: Brief downtime during pod restart (typically a few seconds to a minute)
- **Suitable for**: Development, staging, or production with acceptable restart downtime

### Multi-Instance Deployment (Recommended for Zero-Downtime)

**Run 2+ instances** (1 leader + 1+ backups) for zero-downtime failover:

- **1 leader instance**: Processes all events (outbox publishing or view projections)
- **1+ follower instances**: Ready to take over if leader crashes
- **Zero-downtime failover**: If leader crashes, a follower takes over within 5-30 seconds
- **All instances**: Can handle command API requests (writes) - leader election only affects background processing
- **Suitable for**: Production environments requiring zero-downtime failover

### Scaling

You can run **more than 2 instances**:

- **3+ instances**: Provides additional redundancy
- **Only 1 leader**: Still only one instance processes events
- **Multiple followers**: All ready for failover
- **Command API**: All instances can handle writes (not affected by leader election)

### Kubernetes Deployment

**Single Instance (1 replica):**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: crablet-app
spec:
  replicas: 1  # Single instance - Kubernetes will auto-restart on crash
  template:
    spec:
      containers:
      - name: app
        image: crablet-app:latest
```

**Behavior:**
- Pod starts and acquires leader lock
- If pod crashes → Kubernetes restarts it, new pod acquires lock (brief downtime)
- Suitable when brief restart downtime is acceptable

**Multi-Instance (2+ replicas) - Recommended for Production:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: crablet-app
spec:
  replicas: 2  # 2+ instances for zero-downtime failover
  template:
    spec:
      containers:
      - name: app
        image: crablet-app:latest
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/crablet"
```

**Behavior:**
- Both pods start and attempt to acquire lock
- One pod becomes leader, starts processing
- Other pod(s) become followers, wait for retry
- If leader pod crashes → Another pod takes over as leader within 5-30 seconds (zero downtime)
- If leader pod is gracefully shut down → Lock is released, another pod becomes leader

## Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  Instance A     │         │  Instance B     │         │  Instance C     │
│  (Leader)       │         │  (Follower)     │         │  (Follower)     │
├─────────────────┤         ├─────────────────┤         ├─────────────────┤
│ ✓ Processing    │         │ ○ Waiting       │         │ ○ Waiting       │
│   events        │         │   (retry every  │         │   (retry every  │
│ ✓ Lock held     │         │    30s or 5s)  │         │    30s or 5s)  │
└────────┬────────┘         └────────┬────────┘         └────────┬────────┘
         │                          │                          │
         │                          │                          │
         └──────────────────────────┴──────────────────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │   PostgreSQL                 │
                    │   Advisory Lock              │
                    │   (key: 4856221667890123456) │
                    └───────────────────────────────┘
```

**Leader Crash Scenario:**

```
T0: Instance A crashes (connection closes)
T0: PostgreSQL releases lock automatically
T5: Instance B's processor scheduler runs, detects no leader
T5: Instance B acquires lock, becomes leader
T5: Instance B starts processing events
```

## Implementation Details

### LeaderElectorImpl

The generic leader election implementation:

```java
public class LeaderElectorImpl implements LeaderElector {
    public boolean tryAcquireGlobalLeader() {
        // Uses pg_try_advisory_lock() - non-blocking
        // Returns true if lock acquired, false otherwise
    }
    
    public void releaseGlobalLeader() {
        // Uses pg_advisory_unlock() - releases lock
    }
}
```

### EventProcessorImpl

The event processor coordinates leader election:

```java
// On startup: attempt to acquire lock
leaderElector.tryAcquireGlobalLeader();

// Dedicated retry scheduler (every 30s)
leaderRetryScheduler = taskScheduler.scheduleAtFixedRate(
    this::leaderRetryTask,
    Duration.ofMillis(30000)
);

// Processor schedulers also retry (with 5s cooldown)
if (!leaderElector.isGlobalLeader()) {
    if (cooldownElapsed) {
        leaderElector.tryAcquireGlobalLeader();
    }
}
```

## See Also

- **[Event Processor README](crablet-event-processor/README.md)** - Generic event processing infrastructure
- **[Outbox README](crablet-outbox/README.md)** - Outbox pattern implementation
- **[Views README](crablet-views/README.md)** - View projections implementation

