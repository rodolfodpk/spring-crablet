# PgBouncer Compatibility Guide

## Overview

PgBouncer is a lightweight connection pooler for PostgreSQL that sits between your application and the database. This guide explains how to use PgBouncer with Crablet, compatibility requirements, and deployment patterns.

## What is PgBouncer?

PgBouncer is a middleware component that:
- **Pools connections** at the database level (separate from HikariCP's application-level pooling)
- **Multiplexes connections**: Many application connections → Fewer PostgreSQL connections
- **Load balances** across multiple PostgreSQL servers
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

### Critical: Outbox Requires Session Pooling

**PgBouncer supports three pooling modes:**

| Mode | Connection Lifecycle | Crablet Compatibility |
|------|---------------------|----------------------|
| **Session** | Connection lifetime | ✅ **Fully compatible** |
| Transaction | Transaction lifetime | ⚠️ **Incompatible** (outbox fails) |
| Statement | Statement lifetime | ❌ **Incompatible** |

**Why session pooling is required:**

The outbox uses **PostgreSQL advisory locks** for leader election:

```sql
SELECT pg_try_advisory_lock(4856221667890123456)
```

Advisory locks are **session-scoped** and don't work in transaction pooling mode because PgBouncer may assign different server connections to the same application connection between transactions.

### Configuring PgBouncer for Session Pooling

**File: `/etc/pgbouncer/pgbouncer.ini`**

```ini
[databases]
crablet = host=postgres-server port=5432 dbname=crablet

[pgbouncer]
pool_mode = session  # ← Required for outbox
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

### Option A: Crablet's LoadBalancedDataSource

Let Crablet handle load balancing across replicas:

```properties
# Connect directly to replicas
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.urls=\
  jdbc:postgresql://replica1:5432/crablet,\
  jdbc:postgresql://replica2:5432/crablet
```

**Benefits:**
- Application-level control
- No additional infrastructure

### Option B: PgBouncer for Load Balancing

Let PgBouncer handle load balancing:

```properties
# Connect to single PgBouncer endpoint (PgBouncer balances across replicas)
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.urls=jdbc:postgresql://pgbouncer-read:6432/crablet
```

**Benefits:**
- Single endpoint to manage
- Centralized load balancing logic
- Easier connection pool management

**Configuration:**

```ini
# pgbouncer.ini
[databases]
crablet-read = host=pgbouncer-read-pool port=5432 dbname=crablet

[pgbouncer]
pool_mode = session
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

**Cause:** PgBouncer in transaction pooling mode

**Solution:** Set `pool_mode = session` in `pgbouncer.ini`

### Issue: "advisory lock not available" errors

**Cause:** Multiple connections from same application instance trying to acquire same lock

**Solution:** Ensure only one connection from each instance (check HikariCP pool size)

### Issue: Connection limit exceeded

**Cause:** Too many connections from application instances

**Solution:** Reduce HikariCP pool size or increase PgBouncer `max_client_conn`

## Best Practices

1. **Use session pooling** - Required for outbox advisory locks
2. **Monitor connection counts** - Keep PgBouncer pools healthy
3. **Enable SSL** - Encrypt connections between app and PgBouncer
4. **Use health checks** - Monitor PgBouncer availability
5. **Consider RDS Proxy** - If on AWS, use managed service instead

## See Also

- [Read Replica Guide](./READ_REPLICAS.md) - Crablet's read replica support
- [Performance Optimizations](../../etc/PERFORMANCE_OPTIMIZATIONS.md) - General performance tips
- [PgBouncer Documentation](https://www.pgbouncer.org/) - Official PgBouncer docs

