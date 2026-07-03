# crablet-test-commands

Fast, in-memory BDD base for **command handler unit tests**. No PostgreSQL, no Testcontainers.

It provides `AbstractInMemoryHandlerTest` (`com.crablet.test.commands`), a domain-agnostic
given/when/then base backed by `InMemoryEventStore`. Use it to prove a single handler's decision
logic — happy paths, validation, emitted events and tags. DCB concurrency and persistence belong in
integration tests against the real event store (`AbstractPostgresEventStoreTest` in `crablet-test-support`).

This module sits one layer above `crablet-test-support`: it depends on `crablet-commands` (for
`CommandHandler` / `CommandDecision`) and `crablet-test-support` (for `InMemoryEventStore`). It is
built and installed standalone via the Makefile (`make build-test-commands`), like the other
`crablet-test-*` modules, not as part of the Maven reactor.

## Maven coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-test-commands</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Usage

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
    void givenNoEvents_whenOpeningWallet_thenWalletOpenedEmitted() {
        var events = when(handler, OpenWalletCommand.of("wallet1", "Alice", 1000));
        then(events, WalletOpened.class, w -> assertThat(w.walletId()).isEqualTo("wallet1"));
    }
}
```

See the `/crablet-test-authoring` skill for the full given/when/then API, integration and scenario
test guidance, and the command→event audit-linkage footgun.
