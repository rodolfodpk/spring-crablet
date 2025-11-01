# Crablet EventStore

Event sourcing library with Dynamic Consistency Boundary (DCB) support and Spring Boot integration.

## Overview

Crablet EventStore provides the foundational interfaces and Spring implementations for event sourcing in Java applications. It implements DCB for optimistic concurrency control without requiring distributed locks.

## Features

- **Event Store Interface**: Simple, idiomatic event sourcing API
- **DCB**: Optimistic concurrency control using cursors
- **Type-Safe Commands**: Command handler with automatic projection
- **Flexible Querying**: Tag-based event querying and filtering
- **State Projection**: Built-in support for projecting current state from events
- **Spring Integration**: Ready-to-use Spring Boot components and configuration
- **Read Replicas**: Optional PostgreSQL read replica support for horizontal scaling

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-eventstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- Spring Boot Web, JDBC
- PostgreSQL JDBC Driver
- Jackson (for JSON serialization)
- Resilience4j (for circuit breakers and retries)
- SLF4J (for logging)

## Complete DCB Workflow Example

Two real examples from our wallet domain showing distinct DCB patterns:

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
- Cursor checks if balance changed concurrently → throws `ConcurrencyException` → `CommandExecutor` retries
- Business validation: checks sufficient funds before creating event
- No explicit idempotency check (cursor advancement detects duplicates)

## Learn More

- **[Getting Started](GETTING_STARTED.md)** - Complete integration guide with wallet example
- **[DCB Explained](docs/DCB_AND_CRABLET.md)** - Detailed explanation with examples
- **[Testing](TESTING.md)** - Testcontainers setup and test examples
- **[Database Schema](SCHEMA.md)** - Database tables and functions
- **[Metrics](docs/METRICS.md)** - EventStore metrics and monitoring

## Example Domains

Complete working examples are available in the test scope:

- **Wallet Domain** (`com.crablet.examples.wallet`): Simple wallet with deposits, withdrawals, and transfers
  - Demonstrates: Idempotency, commutative operations, non-commutative operations, multi-entity transfers
- **Course Subscriptions** (`com.crablet.examples.courses`): Course management with student subscriptions
  - Demonstrates: Multi-entity constraints, composite projectors, capacity limits, subscription limits

## License

MIT

