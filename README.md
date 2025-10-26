# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

## Overview

Crablet is a library-first event sourcing solution with Spring Boot integration. It provides:

- **Event Sourcing**: Complete audit trail with state reconstruction
- **DCB Pattern**: Cursor-based optimistic concurrency control without distributed locks
- **Outbox Pattern**: Reliable event publishing to external systems
- **Spring Integration**: Ready-to-use Spring Boot components

## Modules

- **crablet-eventstore** - Core event sourcing library with DCB support
- **crablet-outbox** - Transactional outbox pattern for event publishing

## Quick Start

### Add Dependencies

```xml
<dependencies>
    <!-- EventStore -->
    <dependency>
        <groupId>com.crablet</groupId>
        <artifactId>crablet-eventstore</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Outbox (optional) -->
    <dependency>
        <groupId>com.crablet</groupId>
        <artifactId>crablet-outbox</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Build and Test

Tests use Testcontainers (no external dependencies required):
```bash
mvn clean install
```

All tests pass (260+ tests with 72% code coverage).

## Complete DCB Workflow

Two real examples from our wallet domain showing distinct DCB patterns:

### Example 1: Idempotency Pattern (OpenWallet)

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

    // 4. Build condition to enforce uniqueness using DCB idempotency pattern
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

## Documentation

### Core Documentation
- **[EventStore README](crablet-eventstore/README.md)** - Event sourcing library guide
- **[Outbox README](crablet-outbox/README.md)** - Outbox pattern library guide
- **[DCB Pattern](crablet-eventstore/docs/DCB_AND_CRABLET.md)** - Detailed DCB explanation

### Advanced Features
- **[Read Replicas](crablet-eventstore/docs/READ_REPLICAS.md)** - PostgreSQL read replica configuration
- **[PgBouncer Guide](crablet-eventstore/docs/PGBOUNCER.md)** - Connection pooling
- **[Outbox Pattern](crablet-outbox/docs/OUTBOX_PATTERN.md)** - Event publishing
- **[Outbox Metrics](crablet-outbox/docs/OUTBOX_METRICS.md)** - Monitoring

## Architecture Highlights

- **DCB Pattern**: Optimistic concurrency control using cursors
- **Java 25**: Records, sealed interfaces, virtual threads
- **Spring Boot 3.5**: Full Spring integration
- **PostgreSQL**: Primary database with optional read replicas
- **Comprehensive Testing**: 260+ tests, 72% code coverage

## License

MIT License - see [LICENSE](LICENSE) file for details.
