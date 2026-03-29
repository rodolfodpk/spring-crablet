# Crablet Test Support

Shared test infrastructure for all Crablet modules. Provides fast in-memory testing, Testcontainers base classes, and all framework database migrations in one place.

## Overview

`crablet-test-support` solves two problems:

1. **Test utilities** — `InMemoryEventStore`, `AbstractCrabletTest`, `AbstractHandlerUnitTest`, and `DCBTestHelpers` are shared across all modules from a single dependency instead of being duplicated
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
        eventStore.appendIf(List.of(myEvent), AppendCondition.empty());
        // ...
    }
}
```

### DCBTestHelpers

Utilities for testing DCB concurrency scenarios (optimistic locking, idempotency violations).

## Database Migrations

All framework migrations live in `src/main/resources/db/migration/` — main resources, not test resources — so they appear on the classpath when this module is a dependency:

| File | Creates |
|------|---------|
| `V1__eventstore_schema.sql` | `events`, `commands` tables + functions |
| `V2__outbox_schema.sql` | `outbox_topic_progress` table |
| `V3__view_progress_schema.sql` | `view_progress` table |
| `V4__reaction_progress_schema.sql` | `reaction_progress` table |

Flyway picks these up automatically in every module that declares `crablet-test-support` as a test-scope dependency — no per-module migration copies needed.

## Build Notes

`crablet-test-support` lives outside the reactor (it's not in the parent `pom.xml` modules list) because it depends on `crablet-eventstore` (main scope) while `crablet-eventstore` depends on it (test scope). The Makefile resolves this with a specific build order:

```
1. crablet-eventstore (main only, skip tests)
2. crablet-test-support (full build)
3. shared-examples-domain (full build)
4. Reactor modules (full build with tests)
```

See [BUILD.md](../BUILD.md) for details.

## See Also

- [EventStore TESTING.md](../crablet-eventstore/TESTING.md) — Complete testing strategy guide
- [Command README](../crablet-commands/README.md) — Command framework and test examples
