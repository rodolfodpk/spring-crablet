# Closing the Books Pattern

## Problem Statement

In financial systems, you often need to segment transactions by time periods (monthly statements, daily reports, etc.). Without period segmentation, querying "current balance" requires scanning all historical events, which becomes inefficient as data grows.

### Performance Benefit

The closing books pattern provides a significant performance improvement by allowing you to query only current period events instead of full event history:

- **Without periods**: Querying "current balance" requires scanning all historical events (potentially thousands or millions)
- **With periods**: Only process events from the current period, dramatically reducing event consumption

As your event store grows over time, period-aware queries maintain consistent performance regardless of total history size.

## Overview

The Closing the Books pattern segments events by time periods and manages period boundaries atomically. Each period is represented by:

- `WalletStatementOpened` event: Marks the start of a period with an opening balance
- `WalletStatementClosed` event: Marks the end of a period with a closing balance

Events are tagged with period information (`year`, `month`, `day`, `hour`) allowing queries to filter by period. Periods are created lazily - a `WalletStatementOpened` event is only created when a transaction occurs within that period.

## Key Components

### 1. Period Types

`PeriodType` enum defines the granularity:

```java
public enum PeriodType {
    HOURLY,  // New statement each hour
    DAILY,   // New statement each day
    MONTHLY, // New statement each month
    YEARLY,  // New statement each year
    NONE     // No period segmentation (default)
}
```

See: `crablet-eventstore/src/main/java/com/crablet/eventstore/period/PeriodType.java`

### 2. Period Configuration

> **Important**: The `@PeriodConfig` annotation is **optional**. Commands without this annotation default to `NONE` (no period segmentation) and work normally. This is an **opt-in feature** - you must explicitly add `@PeriodConfig` to enable period segmentation.

Commands are annotated with `@PeriodConfig` to specify period type:

```java
@PeriodConfig(PeriodType.MONTHLY)
public interface WalletCommand {
    String getWalletId();
}
```

You implement a `PeriodConfigurationProvider` component that reads this annotation to determine the period type for a command. If no annotation is present, it should return `PeriodType.NONE`.

**Framework Public API:**
- `PeriodType` enum: `crablet-eventstore/src/main/java/com/crablet/eventstore/period/PeriodType.java`
- `@PeriodConfig` annotation: `crablet-eventstore/src/main/java/com/crablet/eventstore/period/PeriodConfig.java`
- These are in the eventstore module because period segmentation is fundamentally about event organization and querying.

**Domain-Specific Implementation (You Implement):**
- `PeriodConfigurationProvider`: Reads `@PeriodConfig` annotation from command classes
- `PeriodHelper`: Convenience wrapper for period operations (e.g., `WalletPeriodHelper`)
- `PeriodResolver`: Manages period lifecycle and creates statement events (e.g., `WalletStatementPeriodResolver`)
- Statement events: Domain events for period boundaries (e.g., `WalletStatementOpened`, `WalletStatementClosed`)
- Period-aware queries: Query patterns that filter by period tags

**Example Implementation:**
See wallet example in tests: `crablet-eventstore/src/test/java/com/crablet/examples/wallet/period/`

**When to use periods:**
- ✅ Large event histories where querying all events becomes slow
- ✅ Financial systems requiring periodic statements
- ✅ Reporting systems that need time-based segmentation

**When NOT to use periods:**
- ✅ Simple applications with small event volumes
- ✅ Systems where full history queries are fast enough
- ✅ When simplicity is more important than performance

### 3. Period ID

`WalletStatementId` represents a unique period identifier:

- Format: `wallet:{walletId}:{year}-{month}-{day}-{hour}`
- Factory methods: `ofHourly()`, `ofDaily()`, `fromYearMonth()`, `ofYearly()`
- Supports nullable fields (e.g., `day` and `hour` are null for monthly periods)

See: `crablet-eventstore/src/test/java/com/crablet/examples/wallet/period/WalletStatementId.java`

### 4. Statement Events

Two domain events manage period boundaries:

- **`WalletStatementOpened`**: Contains opening balance for the period
- **`WalletStatementClosed`**: Contains closing balance for the period

Both events include period metadata (year, month, day, hour) and are tagged with `statement_id` for idempotency.

See:
- `crablet-eventstore/src/test/java/com/crablet/examples/wallet/event/WalletStatementOpened.java`
- `crablet-eventstore/src/test/java/com/crablet/examples/wallet/event/WalletStatementClosed.java`

### 5. Period Resolver

`WalletStatementPeriodResolver` manages period lifecycle:

- **Lazy creation**: Creates `WalletStatementOpened` only when a transaction occurs in that period
- **Atomic close/open**: When a new period starts, closes previous period and opens new one atomically within the same transaction
- **Balance calculation**: Projects closing balance from previous period to use as opening balance

See: `crablet-eventstore/src/test/java/com/crablet/examples/wallet/period/WalletStatementPeriodResolver.java`

### 6. Period Helper

`WalletPeriodHelper` provides a convenience API for command handlers:

- `ensureActivePeriodAndProject()`: Ensures active period exists and projects balance

This method encapsulates the complexity of period resolution and projection, making it easy for command handlers to use.

See: `crablet-eventstore/src/test/java/com/crablet/examples/wallet/period/WalletPeriodHelper.java`

## How It Works

### 1. Command Handler Flow

**For operations that don't need explicit statement management**, command handlers use `projectCurrentPeriod()`:

```java
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    private final WalletPeriodHelper periodHelper; // Domain-specific helper
    
    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // Project balance for current period (period tags derived from clock, no statement creation)
        var periodResult = periodHelper.projectCurrentPeriod(
            eventStore, command.walletId(), DepositCommand.class);
        
        // Project balance for current period only
        var state = periodResult.projection().state();
        
        // Validate wallet exists
        if (!state.isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }
        
        // Create deposit event with period tags
        var periodId = periodResult.periodId();
        DepositMade deposit = DepositMade.of(
            command.depositId(), command.walletId(), 
            command.amount(), state.balance() + command.amount(), 
            command.description()
        );
        
        AppendEvent.Builder eventBuilder = AppendEvent.builder("DepositMade")
            .tag("wallet_id", command.walletId())
            .tag("deposit_id", command.depositId())
            .tag("year", String.valueOf(periodId.year()))
            .tag("month", String.valueOf(periodId.month()));
        
        // Add day/hour tags conditionally based on period type
        if (periodId.day() != null) {
            eventBuilder.tag("day", String.valueOf(periodId.day()));
        }
        if (periodId.hour() != null) {
            eventBuilder.tag("hour", String.valueOf(periodId.hour()));
        }
        
        AppendEvent event = eventBuilder.data(deposit).build();
        
        // Deposits are commutative - use empty condition
        AppendCondition condition = AppendCondition.empty();
        
        return CommandResult.of(List.of(event), condition);
    }
}
```

**For explicit statement management (Closing Books Pattern)**, use `ensureActivePeriodAndProject()` instead. This method creates `WalletStatementOpened` and `WalletStatementClosed` events when periods change.

**When to use each method:**
- **`projectCurrentPeriod()`**: Use for operations that don't need explicit statement management. Period tags are derived from clock, and balance projection works correctly because transaction events contain cumulative `newBalance` fields. Simpler and more performant.
- **`ensureActivePeriodAndProject()`**: Use when you need explicit statement management (Closing Books Pattern). Creates `WalletStatementOpened` and `WalletStatementClosed` events when periods change, enabling statement-based queries and reporting.

See: `crablet-command/src/test/java/com/crablet/command/handlers/wallet/DepositCommandHandler.java`

### 2. Period Resolution Flow

When `ensureActivePeriodAndProject()` is called:

1. **Check if current period exists**: Query for `WalletStatementOpened` with current period's `statement_id`
2. **If period exists**: Return period ID and project balance
3. **If period doesn't exist**:
   - Calculate previous period
   - If previous period exists and isn't closed:
     - Project closing balance from previous period's events
     - Create and append `WalletStatementClosed` event (only if there were transactions)
   - Calculate opening balance (from previous period's closing balance, or project from all events if first period)
   - Create and append `WalletStatementOpened` event for current period
   - Return current period ID

All period resolution happens within the command handler transaction, ensuring atomicity.

### 3. Period-Aware Queries

Events are queried using period tags via `WalletQueryPatterns`:

```java
Query query = WalletQueryPatterns.singleWalletPeriodDecisionModel(
    walletId, year, month);
```

This query:
- Includes events with matching `year` and `month` tags
- Includes `WalletStatementOpened` to establish opening balance
- Includes `WalletOpened` for wallet existence checks (even though it has no period tags)
- Excludes events from other periods

See: `crablet-eventstore/src/test/java/com/crablet/examples/wallet/WalletQueryPatterns.java` (lines 58-91)

Period-aware queries use `QueryBuilder.matching()` for complex tag combinations:

```java
.matching(
    new String[]{WALLET_STATEMENT_OPENED},
    QueryBuilder.tag(WALLET_ID, walletId),
    QueryBuilder.tag(YEAR, String.valueOf(year)),
    QueryBuilder.tag(MONTH, String.valueOf(month))
)
```

This ensures all tags must match (AND condition) for the event to be included.

## Period Types and Tags

### Monthly Periods (Default)

- Tags: `year`, `month`
- Statement ID: `wallet:{walletId}:{year}-{month}`
- Example: `wallet:alice:2024-01`

### Daily Periods

- Tags: `year`, `month`, `day`
- Statement ID: `wallet:{walletId}:{year}-{month}-{day}`
- Example: `wallet:alice:2024-01-15`

### Hourly Periods

- Tags: `year`, `month`, `day`, `hour`
- Statement ID: `wallet:{walletId}:{year}-{month}-{day}-{hour}`
- Example: `wallet:alice:2024-01-15-14`

## Example: Monthly Statements

```java
// January 2024: Wallet opened, deposits, withdrawals
// Note: Using AppendCondition.empty() for test/setup examples
String txId1 = eventStore.appendIf(List.of(
    AppendEvent.builder("WalletOpened")
        .tag("wallet_id", "alice")
        .tag("year", "2024")
        .tag("month", "1")
        .data(WalletOpened.of("alice", "Alice", 1000))
        .build()
), AppendCondition.empty());

// ... January transactions ...

// End of January: Close statement
// Note: Using AppendCondition.empty() for test/setup examples
String txId2 = eventStore.appendIf(List.of(
    AppendEvent.builder("WalletStatementClosed")
        .tag("wallet_id", "alice")
        .tag("statement_id", "wallet:alice:2024-01")
        .tag("year", "2024")
        .tag("month", "1")
        .data(WalletStatementClosed.of(
            "alice",
            "wallet:alice:2024-01",
            2024, 1, null, null, // year, month, day, hour
            1000,  // opening balance
            1300   // closing balance
        ))
        .build()
), AppendCondition.empty());

// February 2024: Open new statement
// Note: Using AppendCondition.empty() for test/setup examples
String txId3 = eventStore.appendIf(List.of(
    AppendEvent.builder("WalletStatementOpened")
        .tag("wallet_id", "alice")
        .tag("statement_id", "wallet:alice:2024-02")
        .tag("year", "2024")
        .tag("month", "2")
        .data(WalletStatementOpened.of(
            "alice",
            "wallet:alice:2024-02",
            2024, 2, null, null, // year, month, day, hour
            1300  // opening balance from January
        ))
        .build()
), AppendCondition.empty());

// Query February events only
Query febQuery = WalletQueryPatterns.singleWalletPeriodDecisionModel("alice", 2024, 2);
// Returns: WalletOpened, WalletStatementOpened, DepositMade (Feb), WithdrawalMade (Feb)
// Does NOT return: January events
```

## Idempotency

Statement events use idempotency checks to prevent duplicates:

```java
AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
    .withIdempotencyCheck("WalletStatementOpened", STATEMENT_ID, periodId.toStreamId())
    .build();
```

If a `ConcurrencyException` indicates a duplicate (idempotency violation), it's treated as success - the event already exists. This allows safe retries and concurrent period creation attempts.

See: `WalletStatementPeriodResolver.appendOpenEvent()` and `appendCloseEvent()` methods (lines 285-358)

## Transfers Across Periods

Transfers can occur between wallets in different periods:

```java
// Transfer from wallet1 (February) to wallet2 (March)
AppendEvent event = AppendEvent.builder("MoneyTransferred")
    .tag("from_wallet_id", wallet1)
    .tag("from_year", "2024")
    .tag("from_month", "2")
    .tag("to_wallet_id", wallet2)
    .tag("to_year", "2024")
    .tag("to_month", "3")
    .data(transferEvent)
    .build();
```

Use `WalletQueryPatterns.transferPeriodDecisionModel()` to query transfers affecting a wallet in a specific period. This handles the case where wallets may be in different periods independently.

See: `WalletQueryPatterns.transferPeriodDecisionModel()` (lines 127-178)

## Important Implementation Details

### Projector Behavior

The `WalletBalanceProjector` handles statement events carefully:

- **`WalletStatementOpened`**: Sets opening balance but preserves `isExisting()` flag. Wallet existence is determined by `WalletOpened` event, not statement events.
- **`WalletStatementClosed`**: Ignored in balance projection (it's an audit event). Active period queries only see `WalletStatementOpened`.

See: `crablet-eventstore/src/test/java/com/crablet/examples/wallet/projections/WalletBalanceProjector.java` (lines 55-111)

### WalletOpened Events

`WalletOpened` events don't have period tags but are included in period queries to establish wallet existence. This ensures that queries for a period can correctly determine if a wallet exists.

See: `WalletQueryPatterns` lines 61, 132, 148

### First Period Handling

When no previous period exists, the resolver queries all events without period tags to get the initial balance. This is a one-time operation for the first period only.

See: `WalletStatementPeriodResolver.getInitialBalanceForFirstPeriod()` (lines 222-238)

### Transactional Context

Period resolution happens within the command handler transaction, ensuring atomicity. If period creation fails, the entire command fails and rolls back.

### QueryBuilder Usage

Period-aware queries use `QueryBuilder.matching()` for complex tag combinations to ensure all tags must match (AND condition). This is critical for correctly filtering events by period.

See: `WalletQueryPatterns` lines 63-68, 77-89, 134-139

## Best Practices

1. **Use lazy period creation**: Periods are created only when transactions occur, avoiding empty periods
2. **Tag all transaction events**: Ensure all events include period tags for proper filtering
3. **Use period-aware queries**: Always use `WalletQueryPatterns` methods for period queries
4. **Handle idempotency**: Statement events are idempotent - duplicate creation is safe
5. **Project from period queries**: Use period-aware queries when projecting state to ensure correct balances
6. **Include WalletOpened in queries**: Period queries should include `WalletOpened` events to establish wallet existence

## Testing

See `ClosingBooksPatternTest` for comprehensive examples covering:

- Period boundary queries (only current period events returned)
- Balance projection with statement opening
- Sequential event processing (`WalletStatementOpened` first, then transactions)
- Transfers across periods (wallets in different periods)

See: `crablet-eventstore/src/test/java/com/crablet/examples/wallet/ClosingBooksPatternTest.java`

