# Connection Pooler Compatibility Guide

## Overview

This guide covers how to use PostgreSQL connection poolers with Crablet. Three poolers are addressed: **PgBouncer**, **PgCat**, and **OJP** (Open J Proxy).

Crablet intentionally exposes two datasource roles, and that separation is the foundation for all pooler decisions:

- **`WriteDataSource`** — event appends, progress tracking, leader election. Must preserve session semantics.
- **`ReadDataSource`** — event fetches for views, automations, and outbox. Stateless; safe to serve from replicas or transaction-mode pools.

Treat each pooler as transport behind those two roles. Do not rely on a pooler's SQL parser to decide read vs write intent for Crablet.

## Compatibility Matrix

| Feature | PgBouncer | PgCat | OJP |
|---|---|---|---|
| Advisory locks (leader election) | Session mode only | Session mode only | Requires session-affinity validation |
| LISTEN/NOTIFY wakeup | Direct connection required | Direct connection required | Direct connection required |
| WriteDataSource (writes + progress) | Session mode | Session mode | Direct or validated session affinity |
| ReadDataSource (event fetches) | Session or transaction mode | Session or transaction mode | Supported |
| RDS Proxy / managed variant | ⚠️ RDS Proxy not supported for LISTEN | — | — |

## Shared Constraints

These constraints apply to all poolers. Read this section before reading the pooler-specific sections.

### Advisory Locks Require Session-Scoped Connections

Crablet uses `pg_try_advisory_lock` for leader election in the event poller (views, outbox, automations). Advisory locks are session-scoped: if the pooler reassigns a backend connection between transactions, the lock is silently lost.

**Rule:** The endpoint backing `WriteDataSource` must use **session pooling mode**. A single mixed-mode endpoint shared between reads and writes in transaction mode is not sufficient.

### LISTEN/NOTIFY Wakeup Requires a Direct Connection

The event poller supports an optional LISTEN/NOTIFY wakeup mode. When enabled, it opens one dedicated persistent connection, issues `LISTEN <channel>`, and triggers an immediate poll cycle on each notification — reducing latency to milliseconds.

This connection **must bypass all poolers** and point directly at PostgreSQL:

```properties
crablet.event-poller.notifications.jdbc-url=jdbc:postgresql://db-host:5432/mydb
crablet.event-poller.notifications.username=app_user
crablet.event-poller.notifications.password=secret
# optional — must match crablet.eventstore.notifications.channel
crablet.event-poller.notifications.channel=crablet_events
```

Why poolers cannot be used here:
- Transaction-mode poolers (PgBouncer transaction, PgCat transaction) reassign backend connections between transactions. `LISTEN` requires a session that stays alive for the entire application lifecycle.
- RDS Proxy uses transaction-mode pooling internally and cannot support persistent `LISTEN` sessions.
- Routing through any pooler adds an unnecessary failure point for this single long-lived connection.

The `NOTIFY` side (fired by the event store after every append) is a plain SQL call and works through any pooler without restriction.

When wakeup is active, raise the polling interval to 30 s or more. Scheduled polling becomes a safety net rather than the primary latency mechanism.

---

## PgBouncer

PgBouncer is a lightweight connection pooler that operates at the PostgreSQL wire protocol level.

### Pooling Modes

| Mode | Crablet Compatibility |
|---|---|
| **Session** | ✅ Fully compatible |
| **Transaction** | ⚠️ Incompatible for WriteDataSource (advisory locks fail) |
| **Statement** | ❌ Incompatible |

### Recommended Configuration

Use separate endpoints for read and write:

```properties
# Write path — session mode required
spring.datasource.url=jdbc:postgresql://pgbouncer-write:6432/crablet

# Read path — can use a replica-backed pool
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://pgbouncer-read:6432/crablet
```

`pgbouncer.ini` for the write endpoint:

```ini
[databases]
crablet = host=postgres-primary port=5432 dbname=crablet

[pgbouncer]
pool_mode = session
max_client_conn = 1000
default_pool_size = 25
```

For the read endpoint, `pool_mode = session` is the safest option. `transaction` mode is acceptable for Crablet's stateless fetch paths if you have validated your environment.

### AWS RDS Proxy

RDS Proxy is a managed connection pooler available on AWS. It is **not supported** for the LISTEN/NOTIFY wakeup connection because RDS Proxy uses transaction-mode pooling internally. Point `crablet.event-poller.notifications.jdbc-url` directly at the RDS instance.

RDS Proxy is compatible with `WriteDataSource` and `ReadDataSource` for the normal write and read paths.

### Deployment Patterns

**Same host as application:**
```properties
spring.datasource.url=jdbc:postgresql://localhost:6432/crablet
```

**Kubernetes sidecar:**
```yaml
containers:
- name: crablet
  env:
  - name: SPRING_DATASOURCE_URL
    value: "jdbc:postgresql://localhost:6432/crablet"
- name: pgbouncer
  image: edoburu/pgbouncer:latest
  ports:
  - containerPort: 6432
```

### Prepared Statements

Crablet enables prepared statement caching. This is compatible with session pooling mode and has limited compatibility with transaction pooling mode. Use session mode on the write path.

### Troubleshooting

**Outbox not processing / leader election failures:** PgBouncer is in transaction mode on the write path. Set `pool_mode = session`.

**Advisory lock errors:** Multiple connections from the same instance trying to acquire the same lock. Check HikariCP pool size and ensure a single lock-holding connection per instance.

---

## PgCat

PgCat is a PostgreSQL pooler and proxy with support for pooling, query routing, load balancing, and failover.

### Pooling Modes

PgCat's official documentation states:
- Session mode supports prepared statements, `SET`, and advisory locks.
- Transaction mode does **not** support advisory locks.

Use **session mode** on the endpoint backing `WriteDataSource`.

### Recommended Configuration

```properties
# Write path — session mode, primary only
spring.datasource.url=jdbc:postgresql://pgcat-write:6432/crablet

# Read path — replica-backed pool
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://pgcat-read:6432/crablet
```

### Automatic SQL Routing

PgCat can parse SQL and route `SELECT` queries to replicas and writes to the primary. This feature should not be your main read/write boundary for Crablet.

Prefer explicit datasource separation because:
- Crablet already exposes `WriteDataSource` and `ReadDataSource`.
- Leader election must stay on a session-safe primary path.
- Explicit routing is easier to reason about under replication lag and failover.

Treat PgCat's SQL parser as an optimization layer behind `ReadDataSource`, not as the source of truth for correctness.

---

## OJP (Open J Proxy)

[OJP](https://github.com/Open-J-Proxy/ojp) is an open-source Type 3 JDBC driver and Layer 7 proxy. Unlike PgBouncer and PgCat (TCP/protocol level), OJP interposes at the JDBC driver level: the application uses the OJP JDBC driver, which communicates with the OJP proxy server over gRPC, and the proxy manages the actual PostgreSQL connections via a centralized HikariCP pool.

### How OJP Differs from PgBouncer and PgCat

| Aspect | PgBouncer / PgCat | OJP |
|---|---|---|
| Layer | TCP / PostgreSQL wire protocol | JDBC (Type 3) + gRPC |
| Driver | Standard PostgreSQL JDBC driver | OJP JDBC driver (`ojp[host:port]_` URL prefix) |
| Pooling | Server-side, protocol-level | Centralized HikariCP at the OJP server |
| Multi-database | PostgreSQL-focused | PostgreSQL, MySQL, Oracle, SQL Server, and others |

### Connection Configuration

```properties
# Direct PostgreSQL connection (no OJP)
spring.datasource.url=jdbc:postgresql://primary-db:5432/crablet

# Same connection routed through OJP
spring.datasource.url=jdbc:ojp[ojp-server:5000]_postgresql://primary-db:5432/crablet
```

### Advisory Locks and Session Affinity

**OJP's session affinity behavior is not explicitly documented in its public README.** Before deploying OJP in front of `WriteDataSource`, validate that:

1. Advisory locks acquired on one call are still held on subsequent calls within the same application-level connection.
2. The OJP proxy does not reassign the underlying PostgreSQL connection between JDBC operations within a single HikariCP lease.

Until validated, use a direct PostgreSQL connection for `WriteDataSource` and route only `ReadDataSource` through OJP.

### Recommended Architecture

**Conservative (validated safe):**

```properties
# Write path: direct connection — no OJP, session semantics guaranteed
spring.datasource.url=jdbc:postgresql://primary-db:5432/crablet

# Read path: routed through OJP for centralized pooling
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:ojp[ojp-server:5000]_postgresql://replica-db:5432/crablet
```

**Full OJP (requires session-affinity validation):**

```properties
spring.datasource.url=jdbc:ojp[ojp-server:5000]_postgresql://primary-db:5432/crablet
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:ojp[ojp-server:5000]_postgresql://replica-db:5432/crablet
```

### Validation Checklist for Write Path

- [ ] Advisory lock acquired in call N is still held in call N+1 on the same connection
- [ ] `pg_advisory_unlock()` releases the lock correctly
- [ ] No lock loss observed under concurrent poller instances
- [ ] LISTEN connection (if used) survives for the full application lifetime without reassignment

### Connection Storm Protection

OJP's primary value is preventing connection storms when scaling horizontally. All database connections are centralized at the proxy, reducing the connection count seen by PostgreSQL regardless of application instance count. Crablet's read path (event fetches from views, outbox, automations) is stateless and a good candidate for OJP-managed pooling.

---

## Best Practices

1. Use **separate endpoints** for `WriteDataSource` and `ReadDataSource` — match Crablet's two-datasource model explicitly.
2. Use **session mode** on the write path — required for advisory locks.
3. Keep **leader election on the primary path** only — do not route it through a replica-facing pool.
4. Point **`crablet.event-poller.notifications.jdbc-url` directly at PostgreSQL** — never through a pooler.
5. Use **session mode on the read path** unless you have validated transaction pooling in your environment.
6. For OJP: **start with read path only** until session affinity on the write path is validated.
7. Validate **replica lag** against your polling interval and freshness requirements.

## See Also

- [Read Replica Guide](./READ_REPLICAS.md) — Crablet's two-datasource model
- [Leader Election](../../docs/user/LEADER_ELECTION.md) — Advisory lock behavior
- [PgBouncer](https://www.pgbouncer.org/) — Official PgBouncer documentation
- [PgCat](https://github.com/postgresml/pgcat) — Official PgCat project
- [OJP](https://github.com/Open-J-Proxy/ojp) — Official OJP project
