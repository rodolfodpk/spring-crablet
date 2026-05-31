---
name: crablet-local-dev
description: >
  Use this skill for local development with this Crablet app: the MCP codegen loop,
  running tests with Testcontainers, LISTEN/NOTIFY local constraints, and common
  troubleshooting. For framework build internals and module-specific test commands,
  see the spring-crablet root repo skill.
---

# Crablet Local Development

## Codegen Loop

**Claude Code / Cursor (MCP available):**
1. `crablet_plan` — deterministic, reads `event-model.yaml`, no model call; shows planned artifacts
2. Review the planned artifact list before proceeding
3. `crablet_generate` with `output` set to `src/main/java`
4. `./mvnw verify` — compile and run tests

**Terminal / non-MCP workflows:**
1. `make plan`
2. Review
3. `make generate`
4. `make verify`

Never generate before reviewing the plan. The MCP default `output` is `.` — always
pass `src/main/java` for this app.

## Testcontainers

Tests start a PostgreSQL container automatically. No local Postgres install or Docker
Compose required. Docker must be running. If a test hangs on startup, check `docker ps`
for stale containers from a prior failed run.

## LISTEN/NOTIFY Local Constraint

When running the app locally with event poller wakeup enabled,
`crablet.event-poller.notifications.jdbc-url` must be a direct PostgreSQL JDBC URL —
not PgBouncer, PgCat, or RDS Proxy. Testcontainers Postgres is always a direct connection.

## Common Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `crablet_generate` writes to wrong directory | Default `output` is `.` | Pass `src/main/java` explicitly |
| Integration test hangs at startup | Docker not running | Start Docker |
| `ConcurrencyException` in tests | Non-commutative command under parallel load | Serialize test execution |
| LISTEN/NOTIFY not waking poller | Proxy URL instead of direct Postgres | Set `crablet.event-poller.notifications.jdbc-url` to direct JDBC URL |
