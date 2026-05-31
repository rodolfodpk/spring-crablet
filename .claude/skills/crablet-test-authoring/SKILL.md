---
name: crablet-test-authoring
description: >
  Use this skill when the user wants to:
    - Write command handler unit tests for a Crablet app or module
    - Use the BDD given/when/then helpers (AbstractInMemoryHandlerTest)
    - Set up integration tests with a real PostgreSQL (AbstractPostgresEventStoreTest)
    - Write or wire up generated scenario tests
    - Get command->event audit linkage right in a test (the executeInTransaction footgun)
    - Decide which test layer (unit vs integration vs scenario) fits a behavior
argument-hint: [optional: handler or behavior to test]
---

# Crablet Test Authoring

Guidance for writing tests in a Crablet app or framework module. There are three layers;
pick the lowest one that proves the behavior.

## Test layers

| Layer | Base / mechanism | Backing store | Proves |
|-------|------------------|---------------|--------|
| **Handler unit** | `AbstractInMemoryHandlerTest` (given/when/then) | `InMemoryEventStore` | Business logic of a single handler's decision — happy paths, validation, emitted events/tags |
| **Integration** | `AbstractPostgresEventStoreTest` | Testcontainers PostgreSQL | DCB concurrency (`ConcurrencyException`, idempotency), real append, audit linkage, projections |
| **Scenario** | generated `*ScenarioTest` | n/a (stubbed steps) | Event-model scenarios stay represented as living docs; filled in by the author |

Rule of thumb: prove **business logic** at the unit layer (fast, no container); prove
**DCB / concurrency / persistence** at the integration layer. Don't test concurrency in unit tests —
`InMemoryEventStore` does not model it.

## Handler unit tests — `AbstractInMemoryHandlerTest`

`com.crablet.test.commands.AbstractInMemoryHandlerTest` (module **`crablet-test-commands`**) is a
`public abstract`, domain-agnostic base with BDD helpers, backed by `InMemoryEventStore` (fast, no
Postgres). Apps and modules depend on it; they do not copy it.

### Consume crablet-test-commands

In the consuming module's `pom.xml` (test scope). It transitively brings `crablet-commands` and
`crablet-test-support`, so you don't declare those separately for handler unit tests:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-test-commands</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

In-repo, `crablet-commands`' own handler unit tests consume it this way. (Earlier this base shipped
in the `crablet-commands` test-jar; it now has its own module so a fast BDD test no longer pulls the
framework's internal test classes.)

### The given/when/then API

- `given()` — seed prior events into the in-memory store (builder callback matching `AppendEvent.builder()`).
- `when(handler, command)` → `List<Object>` of emitted domain events.
- `whenWithTags(handler, command)` → `List<EventWithTags<Object>>` when you also need to assert tags.
- `then(events, EventType.class, event -> { ... })` — typed assertion on emitted events.
- `then(events, EventType.class, (event, tags) -> { ... })` — assert event data **and** tags.

Override `setUp()` (call `super.setUp()` first) to construct the handler under test.

### Worked example

See `crablet-commands/src/test/java/com/crablet/command/handlers/wallet/unit/OpenWalletCommandHandlerUnitTest.java`
(and the `courses/unit/` siblings). Shape:

```java
class OpenWalletCommandHandlerUnitTest extends AbstractInMemoryHandlerTest {

    private OpenWalletCommandHandler handler;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new OpenWalletCommandHandler();
    }

    @Test
    void givenNoEvents_whenOpeningWallet_thenWalletOpenedEventCreated() {
        // Given: no events

        // When
        var events = when(handler, OpenWalletCommand.of("wallet1", "Alice", 1000));

        // Then — assert event data and tags
        then(events, WalletOpened.class, wallet ->
            assertThat(wallet.walletId()).isEqualTo("wallet1"));
    }
}
```

## Integration tests — `AbstractPostgresEventStoreTest`

Extend `com.crablet.test.AbstractPostgresEventStoreTest` (from `crablet-test-support`) for tests that need a real
PostgreSQL via Testcontainers: DCB append behavior, `ConcurrencyException`, idempotency, projections,
and command->event audit linkage. DCB integration helpers live in
`crablet-test-support/src/main/java/com/crablet/eventstore/integration/`.

### ⚠️ Command→event audit linkage footgun

Linkage between `crablet_commands` and `crablet_events` is by **`transaction_id`** (shared
`pg_current_xact_id()`), not a `command_id` column — this is a closed design decision (see
`/crablet-dcb` and CLAUDE.md > Design Decisions). For a test to get that linkage:

- Run the work inside `executeInTransaction` and call `storeCommand` on the **transaction-scoped**
  store (`ConnectionScopedEventStore`), cast to `CommandAuditStore`.
- Never call `storeCommand` on the top-level `EventStoreImpl` — the transaction ids won't match.

`CommandExecutorImpl` already upholds this in production; only hand-written tests/callers need to be
careful.

## Scenario tests — generated

`crablet_generate` writes one `*ScenarioTest` per `scenarios:` entry in `event-model.yaml` to
`src/test/java`, **written once and never overwritten** (author-owned after first write). They use
only JUnit (`@Test`, `@DisplayName`) with `// Given/When/Then` comment blocks — no Spring, no Crablet —
so they start as living documentation and the author fills in the assertions. See
`examples/loan-generated-snapshot/src/test/java/com/example/loan/test/` for the generated shape, and
`/crablet-codegen` for the contract.

## Running tests

Always use the Makefile targets (see CLAUDE.md > Build Commands). For a focused module:

```bash
make test-pl PL=<module-dir>
make test-pl PL=<module-dir> MVN_ARGS='-Dtest=ClassName'
```

Never run `./mvnw test -pl <module>` without `-am` — it can resolve a stale SNAPSHOT sibling and test
against old migrations. (A PreToolUse hook guards this; see `scripts/hooks/`.)

## Related

- `/crablet-dcb` — choosing AppendCondition, diagnosing `ConcurrencyException`, the linkage invariant
- `/crablet-app-dev` — feature-slice workflow and verifying generated app code
- `/crablet-codegen` — scenario test contract and the generate cycle
