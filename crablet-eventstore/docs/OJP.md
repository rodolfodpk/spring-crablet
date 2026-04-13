# Open J Proxy (OJP) Compatibility Guide

## Overview

[Open J Proxy (OJP)](https://github.com/Open-J-Proxy/ojp) is an open-source Type 3 JDBC driver and Layer 7 proxy server. Unlike PgBouncer or PgCat, which operate at the TCP/protocol level, OJP interposes at the JDBC driver level: the application uses the OJP JDBC driver, which communicates with the OJP proxy server over gRPC, and the proxy manages the actual PostgreSQL connections via a centralized HikariCP pool.

For Crablet, the same two-datasource principle applies:
- `WriteDataSource` — writes, progress tracking, and leader election
- `ReadDataSource` — read-only queries that may be served by replicas

## How OJP Differs from PgBouncer and PgCat

| Aspect | PgBouncer / PgCat | OJP |
|--------|-------------------|-----|
| Layer | TCP / PostgreSQL wire protocol | JDBC (Type 3) + gRPC |
| Driver | Standard PostgreSQL JDBC driver | OJP JDBC driver (`ojp[host:port]_` URL prefix) |
| Pooling | Server-side, protocol-level | Centralized HikariCP at the OJP server |
| Multi-database | PostgreSQL only (PgBouncer) / PostgreSQL-focused | PostgreSQL, MySQL, Oracle, SQL Server, and others |
| Configuration | `.ini` / TOML file on the proxy host | URL prefix on the application side |

## Connection Configuration

OJP uses a URL prefix to redirect connections through the proxy server:

```properties
# Direct PostgreSQL connection (no OJP)
spring.datasource.url=jdbc:postgresql://primary-db:5432/crablet

# Same connection routed through OJP
spring.datasource.url=jdbc:ojp[ojp-server:5000]_postgresql://primary-db:5432/crablet
```

The OJP JDBC driver handles the rest — no other driver changes are required.

## Leader Election and Advisory Locks

Crablet uses PostgreSQL advisory locks for leader election in the event poller:

```sql
SELECT pg_try_advisory_lock(4856221667890123456)
```

Advisory locks are **session-scoped**: the lock is held for the lifetime of the PostgreSQL server connection that acquired it. If the proxy reassigns backend connections between calls, lock ownership is lost.

**OJP's session affinity behavior is not explicitly documented in its public README.** Before deploying OJP in front of the `WriteDataSource`, validate that:

1. Advisory locks acquired on one call are still held on subsequent calls within the same application-level connection.
2. The OJP proxy does not reassign the underlying PostgreSQL connection between JDBC operations within a single HikariCP lease.

Until this is confirmed, treat the write path conservatively:

- Use a **direct PostgreSQL connection** for `WriteDataSource` (bypass OJP for writes and leader election).
- Route only `ReadDataSource` through OJP.

## Recommended Architecture

### Conservative (validated safe)

```properties
# Write path: direct connection — no OJP, session semantics guaranteed
spring.datasource.url=jdbc:postgresql://primary-db:5432/crablet

# Read path: routed through OJP for centralized pooling
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:ojp[ojp-server:5000]_postgresql://replica-db:5432/crablet
```

### Full OJP (requires validation)

If you have confirmed that OJP preserves session affinity on the write path:

```properties
# Write path through OJP — requires session-affinity validation
spring.datasource.url=jdbc:ojp[ojp-server:5000]_postgresql://primary-db:5432/crablet

# Read path through OJP
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:ojp[ojp-server:5000]_postgresql://replica-db:5432/crablet
```

## LISTEN / NOTIFY

The event poller's wakeup mechanism uses a persistent LISTEN connection. This requires:
- A **long-lived, dedicated PostgreSQL connection** that stays open for the duration of the application.
- The connection must not be reassigned or recycled by the proxy.

Configure LISTEN with a **direct PostgreSQL connection**, not through OJP, unless OJP's documentation explicitly supports persistent LISTEN sessions:

```properties
crablet.event-poller.notifications.jdbc-url=jdbc:postgresql://primary-db:5432/crablet
```

## Connection Storm Protection

OJP's primary value proposition is preventing connection storms when scaling application instances horizontally. If you run multiple application instances, OJP can centralize all database connections at the proxy layer, reducing the connection count seen by PostgreSQL regardless of replica count.

This aligns well with Crablet's read path — event fetches from `crablet-views`, `crablet-outbox`, and `crablet-automations` are read-only and stateless, making them good candidates for OJP-managed pooling.

## Validation Checklist

Before running Crablet with OJP on the write path:

- [ ] Advisory lock acquired in call N is still held in call N+1 on the same connection
- [ ] `pg_advisory_unlock()` releases the lock correctly
- [ ] No lock loss observed under concurrent poller instances
- [ ] LISTEN connection (if used) survives for the full application lifetime without reassignment

## Best Practices

1. **Start with OJP on the read path only** — the risk surface is lower; read fetches are stateless.
2. **Use a direct connection for `WriteDataSource`** until session affinity is validated.
3. **Never route LISTEN connections through OJP** unless explicitly documented as supported.
4. **Keep leader election on the primary path** — do not route it through a replica-facing OJP pool.
5. **Monitor HikariCP metrics at the OJP server** — the centralized pool is the single point of connection pressure; keep it healthy.

## See Also

- [Read Replica Guide](./READ_REPLICAS.md) — Crablet datasource model
- [PgBouncer Guide](./PGBOUNCER.md) — PgBouncer compatibility
- [PgCat Guide](./PGCAT.md) — PgCat compatibility
- [Leader Election](../../docs/LEADER_ELECTION.md) — Advisory lock behavior
- [OJP Project](https://github.com/Open-J-Proxy/ojp) — Official OJP project and documentation
