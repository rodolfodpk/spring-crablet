# Read Replica Configuration

## Overview

Crablet supports optional PostgreSQL read replicas to enable horizontal read scaling in production environments. This feature routes read operations (event projections) to a read replica while keeping all write operations on the primary database.

**Important:** Load balancing across multiple replicas is handled externally (AWS RDS read replica endpoints, PgBouncer, pgcat, or hardware load balancers). Crablet connects to a single replica URL that points to your load balancing infrastructure.

## DataSource Configuration

### Primary (Write) DataSource

The primary datasource is automatically configured from Spring Boot's standard datasource properties. Crablet creates a `primaryDataSource` bean from these properties:

```properties
# Primary database configuration (Spring Boot standard properties)
spring.datasource.url=jdbc:postgresql://primary.us-east-1.rds.amazonaws.com:5432/crablet
spring.datasource.username=crablet
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
```

**How it works:**
- Spring Boot auto-configures a `DataSource` from `spring.datasource.*` properties
- Crablet's `DataSourceConfig` registers this as `primaryDataSource` bean (marked `@Primary`)
- All write operations use this datasource: `appendIf()`, `storeCommand()`, outbox position tracking

### Read Replica DataSource

The read replica datasource is configured separately using Crablet-specific properties:

```properties
# Read replica configuration (Crablet-specific properties)
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://read-replica-lb:5432/crablet
crablet.eventstore.read-replicas.hikari.maximum-pool-size=50
crablet.eventstore.read-replicas.hikari.minimum-idle=10
```

**How it works:**
- When `crablet.eventstore.read-replicas.enabled=true`, Crablet creates a `readDataSource` bean
- When disabled (default), `readDataSource` falls back to `primaryDataSource` for seamless compatibility
- All read operations use this datasource: `project()`, `fetchEventsForTopic()`

### Complete Configuration Example

Here's a complete example showing both datasources configured:

```properties
# Primary (write) database - Spring Boot standard properties
spring.datasource.url=jdbc:postgresql://primary.us-east-1.rds.amazonaws.com:5432/crablet
spring.datasource.username=crablet
spring.datasource.password=${DB_PASSWORD}
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5

# Read replica - Crablet-specific properties
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://read-replica-cluster.us-east-1.rds.amazonaws.com:5432/crablet
crablet.eventstore.read-replicas.hikari.maximum-pool-size=50
crablet.eventstore.read-replicas.hikari.minimum-idle=10
```

### Bean Configuration

When configuring `EventStore` bean, inject both datasources:

```java
@Configuration
public class EventStoreConfig {
    
    @Bean
    public EventStore eventStore(
            @Qualifier("primaryDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource,
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock,
            ApplicationEventPublisher eventPublisher) {
        return new EventStoreImpl(writeDataSource, readDataSource, objectMapper, config, clock, eventPublisher);
    }
}
```

**Note:** Both `primaryDataSource` and `readDataSource` beans are automatically created by Crablet's `DataSourceConfig`. You only need to configure the properties above.

## Configuration

### Basic Setup

Read replicas are **disabled by default**. To enable, add the following to `application.properties`:

```properties
# Enable read replica support
crablet.eventstore.read-replicas.enabled=true

# Single read replica JDBC URL (should point to external load balancer)
crablet.eventstore.read-replicas.url=jdbc:postgresql://read-replica-lb:5432/crablet

# HikariCP pool configuration for replica
crablet.eventstore.read-replicas.hikari.maximum-pool-size=50
crablet.eventstore.read-replicas.hikari.minimum-idle=10
```

### AWS RDS Example

```properties
# Primary (write) database - Spring Boot standard properties
spring.datasource.url=jdbc:postgresql://primary.us-east-1.rds.amazonaws.com:5432/crablet
spring.datasource.username=crablet
spring.datasource.password=${DB_PASSWORD}

# Read replica - Crablet-specific properties (AWS RDS read replica endpoint handles load balancing)
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://read-replica-cluster.us-east-1.rds.amazonaws.com:5432/crablet
```

### External Load Balancing Examples

**AWS RDS Read Replica Endpoint:**
```properties
crablet.eventstore.read-replicas.url=jdbc:postgresql://read-replica-cluster.us-east-1.rds.amazonaws.com:5432/crablet
```

**PgBouncer with Multiple Backends:**
```properties
crablet.eventstore.read-replicas.url=jdbc:postgresql://pgbouncer-read:6432/crablet
```

**pgcat Connection Pooler:**
```properties
crablet.eventstore.read-replicas.url=jdbc:postgresql://pgcat-read:5432/crablet
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
- `EventStore.appendIf()` - Conditional event writes with DCB concurrency control
- `EventStore.storeCommand()` - Storing commands
- All outbox position tracking and leader election

### Load Balancing

Crablet **does not implement client-side load balancing**. Instead, it connects to a single replica URL that should point to your external load balancing infrastructure.

**External load balancing options:**
1. **AWS RDS Read Replica Endpoint** - AWS handles load balancing across multiple read replicas
2. **PgBouncer** - Connection pooler with multiple backend servers
3. **pgcat** - Modern PostgreSQL connection pooler
4. **Hardware/Software Load Balancer** - HAProxy, NGINX, or cloud load balancers

**Benefits of external load balancing:**
- ✅ Better failure detection and health checks
- ✅ More sophisticated load balancing algorithms
- ✅ Centralized configuration and monitoring
- ✅ Reduced application complexity

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

## Performance Benefits

### Why Use Read Replicas?

Read replicas provide **horizontal read scaling** by distributing read operations across multiple database instances:

#### 1. **Reduced Primary Database Load**

**Without replicas:**
- All operations (reads + writes) hit the primary database
- Primary database becomes a bottleneck under high read load
- Write performance can degrade when competing with read queries

**With replicas:**
- Read operations (`project()`, `fetchEventsForTopic()`) are routed to replicas
- Primary database handles only writes (`appendIf()`, `storeCommand()`)
- Write performance remains consistent regardless of read load

#### 2. **Horizontal Read Scaling**

**Capacity improvement:**
- **Before:** All reads hit primary database → limited by single instance capacity
- **After:** Reads distributed across N replicas → ~N× read capacity

**Example:**
- Primary database: 1000 reads/second capacity
- With 3 replicas: ~3000 reads/second total capacity (1000 per replica)
- **Result:** 3× read capacity improvement

#### 3. **Better Resource Utilization**

**Primary database:**
- Optimized for write operations (indexes, connection pools tuned for writes)
- No read query overhead
- Consistent write latency

**Read replicas:**
- Optimized for read operations (query caches, read-optimized indexes)
- Can scale independently based on read load
- Different connection pool sizes (typically larger for reads)

#### 4. **Improved Application Throughput**

**Read-heavy workloads:**
- Event projections for command handlers (`project()`)
- Outbox event fetching (`fetchEventsForTopic()`)
- These operations can run in parallel across multiple replicas

**Performance impact:**
- Read queries: Slight latency increase (~10-50ms) due to replica routing
- Write operations: No change (still use primary)
- Overall throughput: Significantly improved for read-heavy applications

### When to Use Read Replicas

**Use read replicas when:**
- ✅ Read operations significantly outnumber writes (80/20 or 90/10 ratio)
- ✅ Read queries are causing primary database bottlenecks
- ✅ You need to scale read capacity beyond single instance limits
- ✅ You can tolerate eventual consistency (replication lag typically <5 seconds)

**Don't use read replicas when:**
- ❌ Write-heavy workloads (replicas won't help)
- ❌ Real-time consistency requirements (replication lag unacceptable)
- ❌ Simple applications with low read load (added complexity not worth it)

### Performance Metrics

Typical performance improvements with read replicas:

| Metric | Without Replicas | With 3 Replicas | Improvement |
|--------|-----------------|-----------------|-------------|
| Read capacity | 1,000 ops/sec | ~3,000 ops/sec | 3× |
| Primary write latency | Variable (competing with reads) | Consistent | More stable |
| Overall throughput | Limited by single instance | Scales horizontally | Significant |
| Read query latency | Direct to primary | +10-50ms (replica routing) | Acceptable trade-off |

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

1. **Use external load balancing** - AWS RDS read replica endpoints, PgBouncer, or pgcat
2. **Monitor replication lag** - Keep under 5 seconds for acceptable outbox latency
3. **Configure multiple replicas** behind your load balancer for redundancy and capacity
4. **Consider PgBouncer** for additional connection pooling (see [PgBouncer Guide](./PGBOUNCER.md))

## Troubleshooting

### Issue: Read replica not being used

**Check:**
1. `crablet.eventstore.read-replicas.enabled=true` is set
2. Replica URL is valid and accessible
3. Application logs show "Fetching from read replica"

### Issue: Connection failures

**Check:**
1. Network connectivity to replica server
2. Replica credentials are correct
3. Firewall rules allow connections
4. PostgreSQL `pg_hba.conf` allows connections
5. External load balancer is healthy and routing correctly

### Issue: Stale data from replicas

**Expected behavior:**
- Replication lag causes slightly stale reads
- Eventual consistency ensures all events are eventually processed
- Increase primary's replication bandwidth if lag is excessive

## See Also

- [PgBouncer Guide](./PGBOUNCER.md) - Using PgBouncer for load balancing
- [Outbox Implementation](../../crablet-outbox/docs/OUTBOX_PATTERN.md) - Understanding outbox implementation
- [Performance Optimizations](../../etc/PERFORMANCE_OPTIMIZATIONS.md) - General performance tips

