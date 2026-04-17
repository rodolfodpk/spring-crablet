# Crablet Test Support

Optional test support for applications built on Crablet. Provides fast in-memory testing, PostgreSQL-backed integration test base classes, and framework database migrations in one place.

## Start Here

- Add this as a `test` dependency when you want shared migrations and reusable Crablet test helpers
- Use `InMemoryEventStore` for fast unit tests
- Use `AbstractCrabletTest` when you need a real PostgreSQL-backed integration test
- Treat this as developer tooling, not as part of your runtime architecture

## Overview

`crablet-test-support` is a public support module for teams testing Crablet-based applications.

It solves two problems:

1. **Test utilities** — `InMemoryEventStore`, `AbstractCrabletTest`, `AbstractHandlerUnitTest`, and `DCBTestHelpers` are available from a single dependency instead of being reimplemented per application
2. **Database migrations** — All framework migrations (V1–V4) live here so every module gets them automatically through a single test-scope dependency

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

### AbstractHandlerUnitTest

BDD-style base class for command handler unit tests. Wraps `InMemoryEventStore` with `given()`, `when()`, `then()` helpers.

```java
class DepositCommandHandlerTest extends AbstractHandlerUnitTest {

    private DepositCommandHandler handler;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new DepositCommandHandler(new WalletBalanceStateProjector());
    }

    @Test
    void givenOpenWallet_whenDeposit_thenDepositMadeEventGenerated() {
        // Given
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet-1", "Alice", 0))
            .tag(WALLET_ID, "wallet-1")
        );

        // When
        DepositCommand command = DepositCommand.of("dep-1", "wallet-1", 100, "Salary");
        List<Object> events = when(handler, command);

        // Then
        then(events, DepositMade.class, event -> {
            assertThat(event.walletId()).isEqualTo("wallet-1");
            assertThat(event.amount()).isEqualTo(100);
        });
    }
}
```

### AbstractCrabletTest

Base class for integration tests using a real PostgreSQL container via Testcontainers.

- Shared container across all tests (reuse enabled)
- Automatic database cleanup before each test
- Dynamic property source for Flyway and datasource configuration

```java
@SpringBootTest(classes = TestApplication.class)
class MyIntegrationTest extends AbstractCrabletTest {

    @Test
    void testWithRealDatabase() {
        // eventStore and jdbcTemplate are autowired from AbstractCrabletTest
        eventStore.appendCommutative(List.of(myEvent));
        // ...
    }
}
```

### DCBTestHelpers

Utilities for testing DCB concurrency scenarios (optimistic locking, idempotency violations).

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
| `V1__eventstore_schema.sql` | `events`, `commands` tables + functions |
| `V2__outbox_schema.sql` | `outbox_topic_progress` table |
| `V3__view_progress_schema.sql` | `view_progress` table |
| `V4__automation_progress_schema.sql` | `automation_progress` table |

Flyway picks these up automatically in every module that declares `crablet-test-support` as a test-scope dependency — no per-module migration copies needed.

## Build Notes

`crablet-test-support` lives outside the reactor (it's not in the parent `pom.xml` modules list) because it depends on `crablet-eventstore` (main scope) while `crablet-eventstore` depends on it (test scope). The Makefile resolves this with a specific build order:

```
1. crablet-eventstore (main only, skip tests)
2. crablet-test-support (full build)
3. shared-examples-domain (full build)
4. Reactor modules (full build with tests)
```

See [Build](../docs/BUILD.md) for details.

## See Also

- [EventStore Testing](../crablet-eventstore/TESTING.md) — Complete testing strategy guide
- [Command README](../crablet-commands/README.md) — Command framework and test examples
