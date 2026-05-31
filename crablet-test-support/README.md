# Crablet Test Support

Optional test support for applications built on Crablet. Provides fast in-memory testing, PostgreSQL-backed integration test base classes, and framework database migrations in one place.

## Start Here

- Add this as a `test` dependency when you want shared migrations and reusable Crablet test helpers
- Use `InMemoryEventStore` for fast unit tests
- Use `AbstractPostgresEventStoreTest` when you need a real PostgreSQL-backed integration test
- Treat this as developer tooling, not as part of your runtime architecture

## Overview

`crablet-test-support` is a public support module for teams testing Crablet-based applications.

It solves two problems:

1. **Test utilities** — `InMemoryEventStore` and `AbstractPostgresEventStoreTest` are available from a single dependency instead of being reimplemented per application (the command-handler BDD base lives one layer up in `crablet-test-commands`)
2. **Database migrations** — All framework migrations live here so every module gets them automatically through a single test-scope dependency

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-test-support</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## What's Included

### InMemoryEventStore

Fast in-memory `EventStore` implementation for unit tests. No database, no Docker, completes in under 10ms.

- Stores original event objects directly (no JSON serialization)
- Uses real `StateProjector` logic for accurate projections
- Accepts all appends — DCB concurrency checks are skipped (test integration tests for that)

```java
InMemoryEventStore eventStore = new InMemoryEventStore();
```

### Command handler BDD base (in `crablet-test-commands`)

The BDD given/when/then base for command handler unit tests — `AbstractInMemoryHandlerTest`
(`com.crablet.test.commands`) — lives in the **`crablet-test-commands`** module, not here. It
depends on `crablet-commands`, so it sits one layer above this module. It wraps `InMemoryEventStore`
(fast, no Postgres) with `given()` / `when()` / `then()` helpers. See the `/crablet-test-authoring`
skill for the dependency snippet and a worked example.

### AbstractPostgresEventStoreTest

Base class for integration tests using a real PostgreSQL container via Testcontainers.

- Shared container across all tests (reuse enabled)
- Automatic database cleanup before each test
- Dynamic property source for Flyway and datasource configuration
- Protected `deserialize(StoredEvent, Class<T>)` helper for asserting persisted JSON event payloads

```java
@SpringBootTest(classes = TestApplication.class)
class MyIntegrationTest extends AbstractPostgresEventStoreTest {

    @Test
    void testWithRealDatabase() {
        // eventStore and jdbcTemplate are autowired from AbstractPostgresEventStoreTest
        eventStore.appendCommutative(List.of(myEvent));
        // ...
    }
}
```

## When To Use This Module

Use `crablet-test-support` when your application needs:

- fast unit tests around command handlers or projections
- PostgreSQL-backed integration tests with framework schema already available
- reusable helpers for DCB-oriented test scenarios

Do not think of this module as part of the runtime product surface. It is optional support tooling for test code.

## Database Migrations

All framework migrations live in `src/main/resources/db/migration/` — main resources, not test resources — so they appear on the classpath when this module is a dependency:

| File | Creates |
|------|---------|
| `V1__crablet_eventstore_schema.sql` | `crablet_events`, `crablet_event_tags`, indexes, and append functions |
| `V2__crablet_commands_schema.sql` | `crablet_commands` |
| `V3__crablet_processing_schema.sql` | `crablet_outbox_topic_progress`, `crablet_view_progress`, `crablet_automation_progress`, and shared-fetch progress tables |

Flyway picks these up automatically in every module that declares `crablet-test-support` as a test-scope dependency — no per-module migration copies needed.

### Integration test database hygiene

Framework integration tests use Flyway (classpath `db/migration` from this module) and truncate the relevant tables from `@BeforeEach` in each module’s `Abstract*Test` base class, or via `com.crablet.test.cleanup.CrabletTestSchemaCleanup` for shared SQL. Example applications keep their own Flyway scripts and test-specific cleanup (for example `wallet-example-app` and `WalletIntegrationTestDbCleanup`).

## Build Notes

`crablet-test-support` lives outside the reactor (it's not in the parent `pom.xml` modules list) because it depends on `crablet-eventstore` (main scope) while `crablet-eventstore` depends on it (test scope). The Makefile resolves this with a specific build order:

```
1. crablet-eventstore (main only, skip tests)
2. crablet-test-support (full build)
3. shared-examples-domain (full build)
4. Reactor modules (full build with tests)
```

See [Build](../docs/user/BUILD.md) for details.

## See Also

- [EventStore Testing](../crablet-eventstore/TESTING.md) — Complete testing strategy guide
- [Command README](../crablet-commands/README.md) — Command framework and test examples
