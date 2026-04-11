# Crablet EventStore

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_eventstore)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light event sourcing framework with Dynamic Consistency Boundary (DCB) support and Spring Boot integration.

## Overview

Crablet EventStore is an event sourcing framework inspired by the [DCB (Dynamic Consistency Boundary)](docs/DCB_AND_CRABLET.md) pattern:

- **DCB**: Criteria-based consistency boundaries using streamPosition-based optimistic locking — no fixed aggregates, no distributed locks for most operations
- **Event Sourcing**: Complete audit trail with state reconstruction
- **Spring Integration**: Ready-to-use Spring Boot components

## Features

- **Event Store Interface**: Simple, idiomatic event sourcing API with three append methods (see below)
- **Flexible Querying**: Tag-based event querying and filtering
- **State Projection**: Built-in support for projecting current state from events
- **Spring Integration**: Ready-to-use Spring Boot components and configuration
- **Read Replicas**: Optional PostgreSQL read replica support for horizontal scaling

### Crablet's Three Append Methods

Crablet maps DCB's consistency model onto three append methods, each with different concurrency semantics:

| Method | Use Case | Concurrency Check |
|--------|----------|-------------------|
| `appendNonCommutative` | State-dependent operations (Withdraw, Transfer) | StreamPosition-based conflict detection |
| `appendCommutative` | Order-independent operations (Deposit, Credit) | None (optional lifecycle guard) |
| `appendIdempotent` | Entity creation (OpenWallet) | Advisory lock uniqueness check |

These method names are Crablet's API — not DCB spec vocabulary. See [DCB_AND_CRABLET.md](docs/DCB_AND_CRABLET.md) for the full explanation.

For most applications, these three append methods are the supported primary API.
Lower-level condition types such as `AppendCondition` and `AppendConditionBuilder`
remain available for advanced direct `EventStore` usage, but they are not the
recommended path for command handlers.

## Usage

Inject `EventStore` and use it directly:

```java
@Autowired
private EventStore eventStore;

public void myOperation() {
    // 1. Project current state (DCB: read current position and state)
    Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(walletId);
    ProjectionResult<WalletBalanceState> projection = eventStore.project(decisionModel, projector);
    
    // 2. Append with streamPosition check (Crablet: appendNonCommutative detects concurrent changes)
    String transactionId = eventStore.appendNonCommutative(events, decisionModel, projection.streamPosition());
}
```

**For Command Framework:** Use `crablet-commands` module for automatic command handler discovery and orchestration.

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-eventstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- Spring Boot JDBC
- Spring Boot Web (test scope only - for integration tests)
- PostgreSQL JDBC Driver
- Jackson (for JSON serialization)
- Resilience4j (for circuit breakers and retries)
- SLF4J (for logging)

## Metrics

EventStore supports metrics collection via Spring's `ApplicationEventPublisher`:

- **Metrics are enabled by default**: Spring Boot automatically provides an `ApplicationEventPublisher` bean
- **Required parameter**: The `eventPublisher` parameter is required in the constructor
- **Automatic metrics collection**: See [crablet-metrics-micrometer](../crablet-metrics-micrometer/README.md) for automatic metrics collection

Example configuration:

```java
@Configuration
public class EventStoreConfig {
    
    @Bean
    public EventStore eventStore(
            WriteDataSource writeDataSource,
            ReadDataSource readDataSource,
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock,
            ApplicationEventPublisher eventPublisher) {
        return new EventStoreImpl(
            writeDataSource.dataSource(),
            readDataSource.dataSource(),
            objectMapper,
            config,
            clock,
            eventPublisher
        );
    }
}
```

The following metrics are published:
- `EventsAppendedMetric` - Events appended to the store
- `EventTypeMetric` - Event types appended
- `ConcurrencyViolationMetric` - Concurrency violations detected

## Tag Key Normalization

Tag keys are automatically normalized to **lowercase** at construction time using `Locale.ROOT`. This applies to all construction paths: `new Tag(key, value)`, `Tag.of(key, value)`, and the varargs overload.

```java
new Tag("WALLET_ID", "abc").key()   // → "wallet_id"
Tag.of("WalletId", "abc").key()     // → "walletid"
```

Tag **values** are never modified — they are case-sensitive entity identifiers.

This prevents silent tag mismatches when a developer writes `.tag("WalletId", id)` on append but queries with `.tag("wallet_id", id)`.

## Period Segmentation with @PeriodConfig

Crablet EventStore supports period segmentation via the `@PeriodConfig` annotation for the closing the books pattern. This enables automatic time-based event segmentation to improve query performance for large event histories.

**Quick start:**

```java
@PeriodConfig(PeriodType.MONTHLY)
public interface WalletCommand {
    String getWalletId();
}
```

**Benefits:**
- ✅ **Performance**: Query only current period events instead of full event history
- ✅ **Flexible**: You implement domain-specific period helpers that read `@PeriodConfig` to create statement events
- ✅ **Opt-in**: Commands without `@PeriodConfig` default to `NONE` and work normally

**Framework vs. Domain:**
- **Framework provides**: `@PeriodConfig` annotation and `PeriodType` enum
- **You implement**: Period helpers, period resolvers, statement events, and period-aware queries (see wallet example in tests)

**Period types:**
- `PeriodType.MONTHLY` - Monthly statements
- `PeriodType.DAILY` - Daily statements  
- `PeriodType.HOURLY` - Hourly statements
- `PeriodType.YEARLY` - Yearly statements
- `PeriodType.NONE` - No period segmentation (default)

**Public API:**
- `@PeriodConfig` annotation: `com.crablet.eventstore.period.PeriodConfig`
- `PeriodType` enum: `com.crablet.eventstore.period.PeriodType`

See [Closing the Books Pattern Guide](docs/CLOSING_BOOKS_PATTERN.md) for complete documentation.

## Crablet Command Pattern Examples

The following examples show Crablet's three concurrency patterns — the library's implementation of DCB-inspired consistency control. `CommandExecutor` automatically calls the correct append method (`appendIdempotent`, `appendCommutative`, or `appendNonCommutative`) based on the `CommandDecision` type returned by the handler.

### Example 1: OpenWallet (Idempotency)

Prevents duplicate wallet creation using `appendIdempotent`:

```java
@Override
public CommandDecision.Idempotent decide(EventStore eventStore, OpenWalletCommand command) {
    // Command is already validated at construction with YAVI

    // 1. Create event (optimistic - assume wallet doesn't exist)
    WalletOpened walletOpened = WalletOpened.of(
            command.walletId(),
            command.owner(),
            command.initialBalance()
    );

    AppendEvent event = AppendEvent.builder(WALLET_OPENED)
            .tag(WALLET_ID, command.walletId())
            .data(walletOpened)
            .build();

    // 2. Return Idempotent decision — CommandExecutor calls appendIdempotent:
    //    - Fails if ANY WalletOpened event exists for this wallet_id
    //    - Append event if wallet doesn't exist
    return CommandDecision.Idempotent.of(event, WALLET_OPENED, WALLET_ID, command.walletId());
}
```

**Key points:**
- No decision model or stream position needed
- `CommandDecision.Idempotent.of(event, type, tagKey, tagValue)` enforces uniqueness atomically
- Optimistic: creates event first, checks at append time

### Example 2: Concurrency Control (Withdraw)

Prevents concurrent balance modifications using streamPosition-based conflict detection:

```java
@Override
public CommandDecision.NonCommutative decide(EventStore eventStore, WithdrawCommand command) {
    // Command is already validated at construction with YAVI

    // Use domain-specific decision model query
    Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());

    // Project state (needed for balance calculation)
    ProjectionResult<WalletBalanceState> projection =
            balanceProjector.projectWalletBalance(eventStore, command.walletId(), decisionModel);
    WalletBalanceState state = projection.state();

    if (!state.isExisting()) {
        log.warn("Withdrawal failed - wallet not found: walletId={}, withdrawalId={}",
                command.walletId(), command.withdrawalId());
        throw new WalletNotFoundException(command.walletId());
    }
    if (!state.hasSufficientFunds(command.amount())) {
        log.warn("Withdrawal failed - insufficient funds: walletId={}, balance={}, requested={}",
                command.walletId(), state.balance(), command.amount());
        throw new InsufficientFundsException(command.walletId(), state.balance(), command.amount());
    }

    int newBalance = state.balance() - command.amount();

    WithdrawalMade withdrawal = WithdrawalMade.of(
            command.withdrawalId(),
            command.walletId(),
            command.amount(),
            newBalance,
            command.description()
    );

    AppendEvent event = AppendEvent.builder(WITHDRAWAL_MADE)
            .tag(WALLET_ID, command.walletId())
            .tag(WITHDRAWAL_ID, command.withdrawalId())
            .data(withdrawal)
            .build();

    // Return NonCommutative decision — CommandExecutor calls appendNonCommutative:
    //    - Check atomically: did any matching events appear after the captured stream position?
    //    - Throw ConcurrencyException if balance changed concurrently
    //    - Append event if condition passes
    return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
}
```

**Key points:**
- Decision Model: Query for balance-affecting events (`WalletOpened`, `DepositMade`, `WithdrawalMade`)
- StreamPosition checks if balance changed concurrently → throws `ConcurrencyException` (application layer handles retry if needed)
- Business validation: checks sufficient funds before creating event
- No explicit idempotency check (streamPosition advancement detects duplicates)

## Learn More

- **[Getting Started](GETTING_STARTED.md)** - Complete integration guide
- **[DCB Explained](docs/DCB_AND_CRABLET.md)** - Detailed explanation with examples
- **[Testing](TESTING.md)** - Testcontainers setup and test examples
- **[Database Schema](SCHEMA.md)** - Database tables and functions
- **[Metrics](docs/METRICS.md)** - EventStore metrics and monitoring
- **[Command Framework](../crablet-commands/README.md)** - Command handling framework built on EventStore

## Example Domains

Complete working examples are available in the `shared-examples-domain` module:

- **Wallet Domain** (`com.crablet.examples.wallet`): Simple wallet with deposits, withdrawals, and transfers
  - Demonstrates: Idempotency, commutative operations, non-commutative operations, multi-entity transfers
- **Course Subscriptions** (`com.crablet.examples.course`): Course management with student subscriptions
  - Demonstrates: Multi-entity constraints, composite projectors, capacity limits, subscription limits

## Test Utilities

Test infrastructure has moved to a dedicated `crablet-test-support` module:

- **InMemoryEventStore** (`com.crablet.test.InMemoryEventStore`) - Fast in-memory event store for unit tests
- **AbstractCrabletTest** (`com.crablet.test.AbstractCrabletTest`) - Base class for integration tests with Testcontainers
- **DCBTestHelpers** (`com.crablet.eventstore.integration.DCBTestHelpers`) - Helper utilities for test event deserialization

See [TESTING.md](TESTING.md) for complete testing guide and examples.

## License

MIT
