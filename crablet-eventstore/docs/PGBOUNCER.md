# PgBouncer Compatibility Guide

## Overview

PgBouncer is a lightweight connection pooler for PostgreSQL that sits between your application and the database. This guide explains how to use PgBouncer with Crablet, compatibility requirements, and deployment patterns.

For Crablet, the important design point is that the framework already distinguishes:
- `primaryDataSource` for writes and leader election
- `readDataSource` for read-only queries that may be served by replicas

Treat PgBouncer as transport and pooling infrastructure behind those two roles, not as the place where Crablet decides read vs write intent.

## What is PgBouncer?

PgBouncer is a middleware component that:
- **Pools connections** at the database level (separate from HikariCP's application-level pooling)
- **Multiplexes connections**: Many application connections → Fewer PostgreSQL connections
- Acts as a **transparent proxy** on the network layer

## Architecture

```
┌─────────────────┐
│ Spring Boot App │
│   (Crablet)     │
└────────┬────────┘
         │
         ↓ JDBC (postgresql.jar)
┌─────────────────┐
│   PgBouncer     │ ← Middleware (optional)
│   (port 6432)    │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│   PostgreSQL    │
│    Database     │
└─────────────────┘
```

From Crablet's perspective, PgBouncer is **transparent** - it's just another database endpoint. The PostgreSQL JDBC driver connects to PgBouncer instead of directly to PostgreSQL.

## Compatibility Requirements

### Critical: Leader Election Requires Session Pooling

**PgBouncer supports three pooling modes:**

| Mode | Connection Lifecycle | Crablet Compatibility |
|------|---------------------|----------------------|
| **Session** | Connection lifetime | ✅ **Fully compatible** |
| Transaction | Transaction lifetime | ⚠️ **Incompatible** (outbox fails) |
| Statement | Statement lifetime | ❌ **Incompatible** |

**Why session pooling is required on the write path:**

Crablet uses **PostgreSQL advisory locks** for leader election:

```sql
SELECT pg_try_advisory_lock(4856221667890123456)
```

Advisory locks are **session-scoped** and don't work in transaction pooling mode because PgBouncer may assign different server connections to the same application connection between transactions.

This affects:
- `crablet-outbox`
- `crablet-views`
- any other processor using the generic leader election infrastructure

At minimum, the endpoint backing `primaryDataSource` must preserve session semantics.

### Configuring PgBouncer for Session Pooling

**File: `/etc/pgbouncer/pgbouncer.ini`**

```ini
[databases]
crablet = host=postgres-server port=5432 dbname=crablet

[pgbouncer]
pool_mode = session  # ← Required for leader election on primaryDataSource
max_client_conn = 1000
default_pool_size = 25
```

## Deployment Patterns

### Pattern 1: Same Host as Application

Deploy PgBouncer on the same server as your Spring Boot application:

```bash
# Install PgBouncer
apt-get install pgbouncer

# Configure
/etc/pgbouncer/pgbouncer.ini

# Start service
systemctl start pgbouncer
```

**Application configuration:**
```properties
spring.datasource.url=jdbc:postgresql://localhost:6432/crablet
```

### Pattern 2: Dedicated Proxy Server

Deploy PgBouncer on a separate proxy server:

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│  App Server 1   │──┐   │                 │      │   PostgreSQL    │
│                 │  │   │  Proxy Server   │      │   Primary       │
└─────────────────┘  │   │  ┌───────────┐  │      │   Replica 1     │
                      ├──→│  │ PgBouncer │  │──┐   │   Replica 2     │
┌─────────────────┐  │   │  └───────────┘  │  │   └─────────────────┘
│  App Server 2   │──┤   │                 │  │
│                 │  │   └─────────────────┘  │
└─────────────────┘  │                          │
                      │                          ↓
┌─────────────────┐  │                   ┌─────────────────┐
│  App Server N   │──┘                   │  Network        │
│                 │                      └─────────────────┘
└─────────────────┘
```

**Application configuration:**
```properties
spring.datasource.url=jdbc:postgresql://pgbouncer-proxy.internal:6432/crablet
```

### Pattern 3: Kubernetes Sidecar

Deploy PgBouncer as a sidecar container in the same pod:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: crablet-app
spec:
  template:
    spec:
      containers:
      - name: crablet
        image: crablet:latest
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://localhost:6432/crablet"
      - name: pgbouncer
        image: edoburu/pgbouncer:latest
        ports:
        - containerPort: 6432
```

### Pattern 4: AWS RDS Proxy (Managed Service)

AWS RDS Proxy is a managed service similar to PgBouncer:

```properties
# Use RDS Proxy endpoint instead of direct RDS endpoint
spring.datasource.url=jdbc:postgresql://crablet-proxy.proxy-xyz.us-east-1.rds.amazonaws.com:5432/crablet
```

**Benefits:**
- ✅ No infrastructure to manage
- ✅ Automatic failover
- ✅ Connection pooling built-in
- ✅ Compatible with Crablet (supports session mode)

## Using PgBouncer with Read Replicas

You have two options for load balancing:

### Recommended Model: Separate Endpoints for Read and Write

Use one PgBouncer endpoint for writes/leader election and another for replica-backed reads:

```properties
# Primary / write path
spring.datasource.url=jdbc:postgresql://pgbouncer-write:6432/crablet

# Read replica path
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://pgbouncer-read:6432/crablet
```

Why this is the preferred model:
- Crablet already encodes read/write intent with two datasources
- leader election and progress updates stay pinned to the primary path
- event fetchers can use a replica-backed pool independently

### Read Pooling Modes

For the endpoint behind `readDataSource`:
- `session` pooling is the safest option
- `transaction` pooling can be acceptable for Crablet's current read-only fetch paths
- `statement` pooling is not recommended

If you are unsure, use `session` for both endpoints.

### If You Want PgBouncer to Spread Reads Across Replicas

Point `readDataSource` at a PgBouncer-managed read endpoint:

```properties
# Single read endpoint used by Crablet
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://pgbouncer-read:6432/crablet
```

If you configure multiple hosts behind one database entry, PgBouncer's `load_balance_hosts` can distribute new server connections across them.

**Configuration example:**

```ini
# pgbouncer.ini
[databases]
crablet-write = host=primary-db port=5432 dbname=crablet
crablet-read = host=replica-a,replica-b port=5432 dbname=crablet

[pgbouncer]
pool_mode = session
load_balance_hosts = round-robin
```

## Prepared Statement Caching

Crablet enables prepared statement caching for performance:

```properties
spring.datasource.hikari.data-source-properties.useServerPrepStmts=true
spring.datasource.hikari.data-source-properties.cachePrepStmts=true
```

**Compatibility:**
- ✅ Works with session pooling mode
- ⚠️ Limited compatibility with transaction pooling mode
- ❌ Not supported in statement pooling mode

## Monitoring

### PgBouncer Stats

PgBouncer provides admin commands to monitor connection pools:

```sql
-- Connect to PgBouncer admin console
psql -p 6432 pgbouncer

-- Show pools
SHOW POOLS;

-- Show clients
SHOW CLIENTS;

-- Show servers
SHOW SERVERS;
```

### Connection Metrics

Monitor connection pool metrics in your application:

```properties
# Enable HikariCP metrics
management.metrics.enable.hikaricp=true
```

Access metrics via Actuator:
```bash
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

## Troubleshooting

### Issue: Outbox not processing events

**Symptom:** Outbox processor doesn't acquire locks, no events published

**Cause:** PgBouncer in transaction pooling mode on the write path

**Solution:** Set `pool_mode = session` in `pgbouncer.ini`

### Issue: "advisory lock not available" errors

**Cause:** Multiple connections from same application instance trying to acquire same lock

**Solution:** Ensure only one connection from each instance (check HikariCP pool size)

### Issue: Connection limit exceeded

**Cause:** Too many connections from application instances

**Solution:** Reduce HikariCP pool size or increase PgBouncer `max_client_conn`

## Best Practices

1. **Use separate endpoints for read and write** - Match Crablet's two-datasource model
2. **Use session pooling on the write path** - Required for advisory locks
3. **Use session pooling on the read path unless you have validated transaction pooling**
4. **Keep leader election on primary-only infrastructure**
5. **Monitor connection counts** - Keep PgBouncer pools healthy
6. **Enable SSL** - Encrypt connections between app and PgBouncer
7. **Use health checks** - Monitor PgBouncer availability
8. **Consider RDS Proxy** - If on AWS, use managed service instead

## See Also

- [Read Replica Guide](./READ_REPLICAS.md) - Crablet's read replica support
- [PgCat Guide](./PGCAT.md) - PgCat compatibility and routing guidance
- [Performance Optimizations](../../etc/PERFORMANCE_OPTIMIZATIONS.md) - General performance tips
- [PgBouncer Documentation](https://www.pgbouncer.org/) - Official PgBouncer docs
