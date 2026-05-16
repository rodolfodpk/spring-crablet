---
name: crablet-local-dev
description: >
  Use this skill for local development with Crablet: build commands and make targets,
  running tests with Testcontainers, the MCP codegen loop, module-specific test runs,
  LISTEN/NOTIFY local constraints, and common troubleshooting. Covers both framework
  contributor workflows (build graph, module tests) and app developer workflows
  (codegen, verify, run).
---

# Crablet Local Development

## Build Graph and Make Targets

`shared-examples-domain`, `examples/wallet-example-app`, and `examples/course-example-app`
are outside the Maven reactor. Always use `make` targets — they apply the correct build
order, stub JAR steps, and `crablet-test-support` migration freshness checks.

| Command | When to use |
|---|---|
| `make install` | Full build with unit tests — normal contributor build |
| `make install-all-tests` | Full build including integration tests |
| `make test` | Run all tests (dependencies already built) |
| `make check-test-support-artifact` | Verify the installed `crablet-test-support` jar matches source Flyway migrations |
| `make clean` | Clean build artifacts |
| `make start` | Run wallet-example-app on port 8080 |
| `make course-start` | Run course-example-app on port 8081 |

**Do not run `./mvnw test -pl <module>` unless that module's dependencies are already built.**
Use `make install` first, or at minimum run `make build-test-support` and
`make check-test-support-artifact`, then target a specific module or test class:

```
./mvnw test -pl crablet-eventstore
./mvnw test -pl crablet-commands -Dtest=CommandExecutorTest
```

## Testcontainers

Integration tests start a PostgreSQL container automatically via Testcontainers.
No local Postgres install or Docker Compose file is required. Docker must be running.
Containers are cleaned up after the test run completes.

Testcontainers uses the installed `crablet-test-support` jar for framework Flyway migrations.
After editing migrations, run `make build-test-support` before focused Maven tests.

If a test hangs on startup, check `docker ps` for stale containers from a prior failed run.

## MCP Codegen Loop

**Claude Code / Cursor (MCP available):**
1. `embabel_plan` — deterministic, reads `event-model.yaml`, no model call; shows planned artifacts
2. Review the planned artifact list before proceeding
3. `embabel_generate` with `output` set to `src/main/java`
4. `./mvnw verify` — compile and run tests

**Codex, terminal, or non-MCP workflows:**
1. `make plan`
2. Review
3. `make generate`
4. `make verify`

Never generate before reviewing the plan.

## LISTEN/NOTIFY Local Constraint

When running the app locally with event poller wakeup enabled,
`crablet.event-poller.notifications.jdbc-url` must be a direct PostgreSQL JDBC URL.
PgBouncer in transaction mode, PgCat, and RDS Proxy do not support NOTIFY.

Testcontainers-managed Postgres is always a direct connection — no special configuration
needed for tests.

## Common Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `UnsatisfiedDependencyException` on startup | Module dependency not built | Run `make install` |
| Flyway migration mismatch in focused tests | Stale installed `crablet-test-support` jar | Run `make build-test-support`, then `make check-test-support-artifact` |
| Integration test hangs at startup | Docker not running | Start Docker |
| `embabel_generate` writes to wrong directory | Custom `output` arg pointing at wrong path | Omit `output` to use the default (`src/main/java`) or pass the correct path explicitly |
| `ConcurrencyException` in tests | Expected for non-commutative commands under parallel load | Serialize test execution or use `@DirtiesContext` |
| LISTEN/NOTIFY not waking poller | Proxy URL instead of direct Postgres | Set `crablet.event-poller.notifications.jdbc-url` to direct JDBC URL |
| Example app not in reactor build | `examples/` excluded from Maven reactor | Use `make start` / `make course-start` |
