# Read Replica Configuration

## Overview

Crablet supports optional PostgreSQL read replicas to enable horizontal read scaling in production environments. This feature routes read operations (event projections) to read replicas while keeping all write operations on the primary database.

## Configuration

### Basic Setup

Read replicas are **disabled by default**. To enable, add the following to `application.properties`:

```properties
# Enable read replica support
crablet.eventstore.read-replicas.enabled=true

# Comma-separated list of read replica JDBC URLs
crablet.eventstore.read-replicas.urls=jdbc:postgresql://replica1.aws.com:5432/crablet,jdbc:postgresql://replica2.aws.com:5432/crablet

# Fallback to primary if all replicas fail (recommended)
crablet.eventstore.read-replicas.fallback-to-primary=true

# HikariCP pool configuration for replicas
crablet.eventstore.read-replicas.hikari.maximum-pool-size=50
crablet.eventstore.read-replicas.hikari.minimum-idle=10
```

### AWS RDS Example

```properties
# Primary (write) database
spring.datasource.url=jdbc:postgresql://primary.us-east-1.rds.amazonaws.com:5432/crablet
spring.datasource.username=crablet
spring.datasource.password=${DB_PASSWORD}

# Read replicas
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.urls=\
  jdbc:postgresql://replica-1.us-east-1.rds.amazonaws.com:5432/crablet,\
  jdbc:postgresql://replica-2.us-east-1.rds.amazonaws.com:5432/crablet
crablet.eventstore.read-replicas.fallback-to-primary=true
```

### Credentials

By default, read replicas use the same credentials as the primary database. To use different credentials:

```properties
crablet.eventstore.read-replicas.hikari.username=replica-user
crablet.eventstore.read-replicas.hikari.password=${REPLICA_PASSWORD}
```

## Architecture

### How It Works

**Read Operations (use read replicas):**
- `EventStore.project()` - Event queries for state projection
- `OutboxProcessor.fetchEventsForTopic()` - Fetching events for publishing

**Write Operations (use primary):**
- `EventStore.append()` - Writing new events
- `EventStore.appendIf()` - Conditional event writes
- `EventStore.storeCommand()` - Storing commands
- All outbox position tracking and leader election

### Load Balancing

Crablet uses a **simple round-robin** strategy to distribute reads across available replicas. Each `getConnection()` call rotates to the next replica in the list.

**Load balancing strategy:**
1. Try each replica in order (round-robin)
2. If all replicas fail and `fallback-to-primary=true`, use primary database
3. If fallback disabled and all replicas fail, throw `SQLException`

## Architectural Decision: Two-Step Outbox Query Pattern

### Why Separate Queries?

The outbox processor uses a **two-step query pattern**:

```java
// Step 1: Read last position from primary
lastPosition = getLastPosition(topic, publisher);

// Step 2: Fetch events from replica
events = fetchEventsForTopic(lastPosition);
```

### Why Not a Single JOIN Query?

**Hypothetical JOIN query:**
```sql
SELECT e.* 
FROM events e
JOIN outbox_topic_progress otp ON e.position > otp.last_position
WHERE otp.topic = ? AND otp.publisher = ?
```

**Problem:** This query requires access to **both tables** (`events` and `outbox_topic_progress`) from the **same database**. We can't JOIN across different DataSources (primary vs replica).

**Solution:** Separate queries allow:
- Position tracking → Primary (strong consistency)
- Event fetching → Replica (eventual consistency acceptable)

### Trade-offs

**Benefits:**
- ✅ Offloads read queries from primary database
- ✅ No gaps in event processing (events still delivered eventually)
- ✅ Scales horizontally with more replicas

**Constraints:**
- ⚠️ Replication lag delays event publishing by (lag + poll interval)
- ⚠️ Acceptable delay for most use cases (typically seconds)
- ⚠️ Not suitable if real-time publishing is critical

## Performance Expectations

### Typical Latency Impact

| Operation | Without Replicas | With Replicas | Impact |
|-----------|-----------------|---------------|---------|
| Event reads | Direct to primary | Via replica (round-robin) | Slight increase (~10-50ms) |
| Event writes | Direct to primary | Direct to primary | No change |
| Outbox processing | Real-time | Lagged by replication delay | Acceptable for most cases |

### Capacity Improvement

Adding read replicas provides **horizontal read scaling**:

- **Before:** All reads hit primary database
- **After:** Reads distributed across N replicas
- **Expected improvement:** ~N× read capacity (assuming even distribution)

## Monitoring

### Metrics

Monitor replication lag using PostgreSQL metrics:

```sql
-- Check replication lag
SELECT 
    application_name,
    client_addr,
    state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    replay_lag
FROM pg_stat_replication;
```

### Health Checks

Crablet logs connection failures when replicas are unavailable:

```
WARN - Failed to connect to replica: Connection refused
INFO - Falling back to primary database
```

### Recommendations

1. **Enable fallback** (`fallback-to-primary=true`) for resilience
2. **Monitor replication lag** - Keep under 5 seconds for acceptable outbox latency
3. **Use multiple replicas** for redundancy and capacity
4. **Consider PgBouncer** for additional load balancing (see [PgBouncer Guide](./PGBOUNCER.md))

## Troubleshooting

### Issue: Read replica not being used

**Check:**
1. `crablet.eventstore.read-replicas.enabled=true` is set
2. Replica URLs are valid and accessible
3. Application logs show "Fetching from read replica"

### Issue: Connection failures

**Check:**
1. Network connectivity to replica servers
2. Replica credentials are correct
3. Firewall rules allow connections
4. PostgreSQL `pg_hba.conf` allows connections

### Issue: Stale data from replicas

**Expected behavior:**
- Replication lag causes slightly stale reads
- Eventual consistency ensures all events are eventually processed
- Increase primary's replication bandwidth if lag is excessive

## See Also

- [PgBouncer Guide](./PGBOUNCER.md) - Using PgBouncer for load balancing
- [Outbox Pattern](../../architecture/OUTBOX_PATTERN.md) - Understanding outbox implementation
- [Performance Optimizations](../../etc/PERFORMANCE_OPTIMIZATIONS.md) - General performance tips

