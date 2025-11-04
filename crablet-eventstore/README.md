# Crablet EventStore

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_eventstore)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light event sourcing library with Dynamic Consistency Boundary (DCB) support and Spring Boot integration. Use as a library for direct EventStore access.

## Overview

Crablet EventStore is a pure event sourcing library:

- **Library**: Direct `EventStore` access for full control over event operations
- **DCB**: Optimistic concurrency control without distributed locks
- **Flexible**: Can be used standalone or with command framework

**Library Benefits:**
- Pure library - no framework overhead
- Full control over operations
- No required interfaces to implement - just inject `EventStore` and use `append()` for basic event storage
- Optional: Implement `StateProjector<T>` if you need DCB concurrency control (which requires using `appendIf()` with state projections)
- Easy to customize and extend

## Features

- **Event Store Interface**: Simple, idiomatic event sourcing API
- **DCB**: Optimistic concurrency control using cursors
- **Flexible Querying**: Tag-based event querying and filtering
- **State Projection**: Built-in support for projecting current state from events
- **Spring Integration**: Ready-to-use Spring Boot components and configuration
- **Read Replicas**: Optional PostgreSQL read replica support for horizontal scaling

## Usage

Use `EventStore` directly for full control:

```java
@Autowired
private EventStore eventStore;

public void myCustomOperation() {
    // Direct access, no framework required
    eventStore.append(events);
    ProjectionResult<State> result = eventStore.project(query, cursor, State.class, projectors);
}
```

**Benefits:**
- Full control over operations
- No framework overhead
- No required interfaces
- Flexible for custom use cases

**For Command Framework:** Use `crablet-command` module for automatic command handler discovery and orchestration.

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

## DCB Pattern Examples

Examples showing distinct DCB patterns:

### Example 1: OpenWallet (Idempotency)

Prevents duplicate wallet creation using `withIdempotencyCheck()`:

```java
@Override
public CommandResult handle(EventStore eventStore, OpenWalletCommand command) {
    // Command is already validated at construction with YAVI

    // 2. DCB: NO validation query needed!
    //    AppendCondition will enforce uniqueness atomically

    // 3. Create event (optimistic - assume wallet doesn't exist)
    WalletOpened walletOpened = WalletOpened.of(
            command.walletId(),
            command.owner(),
            command.initialBalance()
    );

    AppendEvent event = AppendEvent.builder(WALLET_OPENED)
            .tag(WALLET_ID, command.walletId())
            .data(walletOpened)
            .build();

    // 4. Build condition to enforce uniqueness using DCB idempotency
    //    Fails if ANY WalletOpened event exists for this wallet_id (idempotency check)
    //    No concurrency check needed for wallet creation - only idempotency matters
    AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
            .withIdempotencyCheck(WALLET_OPENED, WALLET_ID, command.walletId())
            .build();

    // 5. Return - appendIf will:
    //    - Check condition atomically
    //    - Throw ConcurrencyException if wallet exists
    //    - Append event if wallet doesn't exist
    return CommandResult.of(List.of(event), condition);
}
```

**Key points:**
- Uses `Query.empty()` + `Cursor.zero()` (no decision model needed)
- `withIdempotencyCheck()` enforces uniqueness: fails if `WALLET_OPENED` exists for this `wallet_id`
- Optimistic: creates event first, checks atomically via `appendIf`

### Example 2: Concurrency Control (Withdraw)

Prevents concurrent balance modifications using cursor-based conflict detection:

```java
@Override
public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
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

    // Build condition: decision model only (cursor-based concurrency control)
    // DCB Principle: Cursor check prevents duplicate charges
    // Note: No idempotency check - cursor advancement detects if operation already succeeded
    AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
            .build();

    return CommandResult.of(List.of(event), condition);
}
```

**Key points:**
- Decision Model: Query for balance-affecting events (`WalletOpened`, `DepositMade`, `WithdrawalMade`)
- Cursor checks if balance changed concurrently → throws `ConcurrencyException` (application layer handles retry if needed)
- Business validation: checks sufficient funds before creating event
- No explicit idempotency check (cursor advancement detects duplicates)

## Learn More

- **[Getting Started](GETTING_STARTED.md)** - Complete integration guide
- **[DCB Explained](docs/DCB_AND_CRABLET.md)** - Detailed explanation with examples
- **[Testing](TESTING.md)** - Testcontainers setup and test examples
- **[Database Schema](SCHEMA.md)** - Database tables and functions
- **[Metrics](docs/METRICS.md)** - EventStore metrics and monitoring
- **[Command Framework](../crablet-command/README.md)** - Command handling framework built on EventStore

## Example Domains

Complete working examples are available in the test scope (accessible via test-jar):

- **Wallet Domain** (`com.crablet.examples.wallet`): Simple wallet with deposits, withdrawals, and transfers
  - Demonstrates: Idempotency, commutative operations, non-commutative operations, multi-entity transfers
- **Course Subscriptions** (`com.crablet.examples.courses`): Course management with student subscriptions
  - Demonstrates: Multi-entity constraints, composite projectors, capacity limits, subscription limits

## License

MIT

