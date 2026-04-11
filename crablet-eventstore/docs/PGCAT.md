# PgCat Compatibility Guide

## Overview

PgCat is a PostgreSQL pooler and proxy with support for pooling, query routing, load balancing, and failover. This guide explains how to use PgCat with Crablet's two-datasource model.

Crablet already distinguishes:
- `primaryDataSource` for writes, progress tracking, and leader election
- `readDataSource` for read-only queries that may be served by replicas

That separation should stay explicit even when PgCat is present.

## Compatibility Summary

### Write Path: Session Mode Required

Crablet uses PostgreSQL advisory locks for leader election in the event poller used by:
- `crablet-outbox`
- `crablet-views`

PgCat's official documentation states:
- session mode supports prepared statements, `SET`, and advisory locks
- transaction mode does **not** support advisory locks

Therefore, the endpoint behind `primaryDataSource` must use **session mode**.

### Read Path: Separate Pool Preferred

For `readDataSource`, Crablet's internal fetch paths are read-only. That means:
- session mode is the safest default
- transaction mode may be acceptable for replica-backed reads if you have validated your environment

Do not send leader election or other session-scoped operations through a transaction-mode PgCat pool.

## Recommended Architecture

Use separate PgCat endpoints or pools for read and write intent:

```properties
# Write path: primary only, session-safe
spring.datasource.url=jdbc:postgresql://pgcat-write:6432/crablet

# Read path: replicas
crablet.eventstore.read-replicas.enabled=true
crablet.eventstore.read-replicas.url=jdbc:postgresql://pgcat-read:6432/crablet
```

Why this model fits Crablet:
- the framework already knows which operations are reads and writes
- progress tracking and leader election stay on the primary path
- event fetches can use replica-backed infrastructure without leaking that concern into application code

## Avoid Relying on Automatic SQL Routing

PgCat can parse SQL and route `SELECT` queries to replicas and writes to the primary. That feature is useful in some applications, but it should not be your main read/write boundary for Crablet.

Prefer explicit datasource separation because:
- Crablet already exposes `primaryDataSource` and `readDataSource`
- leader election must stay on a session-safe primary path
- explicit routing is easier to reason about under replication lag and failover

Treat PgCat's parser as an optimization layer behind `readDataSource`, not as the source of truth for correctness.

## Deployment Guidance

### Primary / Write Endpoint

Requirements:
- routes only to the primary
- uses session mode
- supports long-lived server sessions for advisory locks

This endpoint is used for:
- event appends
- progress tracking
- outbox status and management writes
- leader election

### Replica / Read Endpoint

Requirements:
- routes to replicas or a replica set
- does not need advisory-lock support
- should tolerate replica lag appropriate to your polling interval

This endpoint is used for:
- event fetches
- projections built from reads

## PgCat and Advisory Locks

Advisory locks are session-scoped PostgreSQL features. If the pooler reassigns backend connections between transactions, lock ownership is no longer stable.

For Crablet, that means:
- `primaryDataSource` must not use transaction-mode pooling for processors that elect leaders
- a single mixed read/write PgCat endpoint in transaction mode is not sufficient

## Best Practices

1. Use separate PgCat pools or endpoints for read and write intent.
2. Use session mode on the write path.
3. Keep leader election on the primary path only.
4. Use replica-backed PgCat only for `readDataSource`.
5. Validate replica lag against your poll interval and freshness requirements.
6. Prefer explicit datasource wiring over SQL-parser-based routing for correctness.

## See Also

- [Read Replica Guide](./READ_REPLICAS.md) - Crablet datasource model
- [PgBouncer Guide](./PGBOUNCER.md) - PgBouncer compatibility
- [Leader Election](../../docs/LEADER_ELECTION.md) - Advisory lock behavior
- [PgCat Project](https://github.com/postgresml/pgcat) - Official PgCat project and documentation
