# Crablet Command Framework

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_command)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light command handling framework for event sourcing with automatic handler discovery and Spring Boot integration.

## Overview

Crablet Command provides a lightweight framework for command handling on top of Crablet EventStore:

- **Automatic Handler Discovery**: Handlers are auto-discovered via Spring `@Component` annotation
- **Type-Safe Commands**: Command handler with automatic projection
- **Type Extraction**: Command types extracted from handler's generic type parameter
- **Transaction Management**: Automatic transaction lifecycle management
- **DCB Support**: Full support for Dynamic Consistency Boundary pattern

**Light Framework Benefits:**
- Required: Implement `CommandHandler<T>` (one per command type)
- Use: Inject `CommandExecutor` and `EventStore` (provided by framework)
- Small API surface: 1 interface to implement
- Easy to customize and extend

## Features

- **CommandHandler Interface**: Type-safe command handling with self-identification
- **CommandExecutor**: Automatic command execution with handler discovery
- **Type-Safe Registration**: Command types extracted from handler's generic type parameter
- **Single Source of Truth**: `@JsonSubTypes` annotation defines command types
- **Spring Integration**: Ready-to-use Spring Boot components and configuration

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-command</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Note**: This module depends on `crablet-eventstore`. You must also include:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-eventstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-eventstore (required)
- Spring Boot JDBC
- Spring Boot Web (test scope only - for integration tests)
- Jackson (for JSON serialization)
- Resilience4j (for circuit breakers and retries)
- SLF4J (for logging)

## Quick Start

### 1. Define Command Interface

Commands must implement an interface annotated with `@JsonSubTypes`:

```java
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "commandType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DepositCommand.class, name = "deposit"),
        @JsonSubTypes.Type(value = WithdrawCommand.class, name = "withdraw"),
        @JsonSubTypes.Type(value = OpenWalletCommand.class, name = "open_wallet")
})
public interface WalletCommand {
    String getWalletId();
}
```

### 2. Implement CommandHandler

```java
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // 1. Project decision model
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        
        // 2. Project state
        ProjectionResult<WalletBalanceState> projection =
                balanceProjector.projectWalletBalance(eventStore, command.walletId(), decisionModel);
        
        // 3. Validate business rules
        if (!projection.state().isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }
        
        // 4. Create events
        DepositMade depositMade = DepositMade.of(...);
        AppendEvent event = AppendEvent.builder("DepositMade")
                .tag("wallet_id", command.walletId())
                .data(depositMade)
                .build();
        
        // 5. Build condition (DCB pattern)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
                .build();
        
        return CommandResult.of(List.of(event), condition);
    }
}
```

### 3. Configure CommandExecutor

```java
@Configuration
public class CrabletConfig {
    
    @Bean
    public CommandExecutor commandExecutor(
            EventStore eventStore,
            List<CommandHandler<?>> commandHandlers,
            EventStoreConfig config,
            ClockProvider clock,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }
}
```

### 4. Execute Commands

```java
@Service
public class WalletService {
    
    private final CommandExecutor commandExecutor;
    
    public WalletService(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }
    
    public ExecutionResult deposit(String walletId, String depositId, int amount) {
        DepositCommand command = DepositCommand.of(depositId, walletId, amount, "Salary");
        return commandExecutor.executeCommand(command);
    }
}
```

## Automatic Handler Registration

- Handlers implementing `CommandHandler<T>` are auto-discovered by Spring
- Command type is automatically extracted from the handler's generic type parameter
- Uses reflection to read `@JsonSubTypes` annotation on the command interface
- Duplicate handlers for the same command type are detected at startup

## Transaction Management

### Single Transaction for All Operations

`CommandExecutor` executes all operations within a single database transaction:

```java
eventStore.executeInTransaction(txStore -> {
    // 1. Handler queries/projects state (uses same transaction)
    CommandResult result = handler.handle(txStore, command);
    
    // 2. Append events (uses same transaction, returns transactionId)
    String transactionId = txStore.appendIf(result.events(), result.appendCondition());
    
    // 3. Store command (uses same transaction, uses transactionId from step 2)
    txStore.storeCommand(commandJson, commandType, transactionId);
    
    // All operations commit atomically, or all rollback on error
});
```

### How It Works

1. **Transaction Start**: `executeInTransaction()` creates a single `Connection` with `autoCommit(false)`
2. **ConnectionScopedEventStore**: All operations receive a `ConnectionScopedEventStore` that uses the same `Connection`
3. **Handler Queries**: When handler calls `txStore.project()`, it uses `projectWithConnection(connection, ...)` - same transaction
4. **Event Append**: When `txStore.appendIf()` is called, it uses `appendIfWithConnection(connection, ...)` - same transaction
5. **Command Storage**: When `txStore.storeCommand()` is called, it uses `storeCommandWithConnection(connection, ...)` - same transaction
6. **Atomic Commit**: All operations commit together, or all rollback on any error

### Transaction ID

The `transactionId` is generated by PostgreSQL's `pg_current_xact_id()` function when `appendIf()` is called:

```sql
-- Inside append_events_if function:
'transaction_id', pg_current_xact_id()::TEXT
```

**Important points:**
- ✅ **Same transaction ID**: All operations (queries, append, command storage) use the same transaction ID
- ✅ **Generated on append**: The transactionId is only available after `appendIf()` returns it
- ✅ **Represents entire transaction**: `pg_current_xact_id()` returns the ID of the current transaction, which is the same for all operations in that transaction

**Timeline:**
1. Handler queries state → Uses transaction (transactionId exists but not yet read)
2. Handler calls `appendIf()` → PostgreSQL function returns transactionId via `pg_current_xact_id()`
3. Command storage → Uses the transactionId from step 2, still in same transaction

### Atomicity Guarantees

All operations are atomic:
- ✅ **Queries see consistent snapshot**: All queries in the handler see the same database state
- ✅ **Append is atomic**: Events are appended atomically with concurrency checks
- ✅ **Command storage is atomic**: Command is stored atomically with events
- ✅ **All-or-nothing**: If any operation fails, the entire transaction rolls back

This ensures that:
- Queries and appends are consistent (no race conditions between read and write)
- Command and events are stored together (no orphaned commands)
- DCB concurrency checks work correctly (cursor position is consistent with appended events)

## Command Patterns

Crablet Command supports different DCB patterns:

- **Idempotency Check**: `withIdempotencyCheck()` for entity creation
- **Cursor-based Check**: `AppendConditionBuilder(decisionModel, cursor)` for state-dependent operations
- **Empty Condition**: `AppendCondition.empty()` for commutative operations (mainly used in tests)

See [Command Patterns Guide](../crablet-eventstore/docs/COMMAND_PATTERNS.md) for complete examples.

## Closing the Books Pattern with @PeriodConfig

The closing the books pattern segments events by time periods (monthly, daily, hourly, yearly) to improve query performance for large event histories. Use the `@PeriodConfig` annotation on command interfaces to enable automatic period segmentation:

```java
@PeriodConfig(PeriodType.MONTHLY)
public interface WalletCommand {
    String getWalletId();
}
```

**How it works:**
1. **Annotation-based configuration**: Commands annotated with `@PeriodConfig` automatically enable period segmentation
2. **Domain-specific period helpers**: You implement period helpers (e.g., `WalletPeriodHelper`) that read `@PeriodConfig` and automatically create `StatementOpened` and `StatementClosed` events when needed
3. **Period-aware queries**: Events are tagged with period metadata (`year`, `month`, `day`, `hour`) allowing queries to filter by period
4. **Performance benefit**: Query only current period events instead of full event history

**Framework vs. Domain:**
- **Framework provides**: `@PeriodConfig` annotation and `PeriodType` enum
- **You implement**: Period helpers, period resolvers, statement events, and period-aware queries (see wallet example in tests)

**Period types:**
- `PeriodType.MONTHLY` - Monthly statements (default for financial systems)
- `PeriodType.DAILY` - Daily statements
- `PeriodType.HOURLY` - Hourly statements
- `PeriodType.YEARLY` - Yearly statements
- `PeriodType.NONE` - No period segmentation (default when annotation is absent)

**Example usage in command handler:**

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

**Note:** `projectCurrentPeriod()` determines the current period from the clock and projects balance without creating statement events. For explicit statement management (Closing Books Pattern), use `ensureActivePeriodAndProject()` instead.

**Note:** `WalletPeriodHelper` is a domain-specific example. You'll need to implement your own period helper based on your domain's requirements. The framework only provides `@PeriodConfig` and `PeriodType` - period resolution logic is domain-specific.

**Important:**
- `@PeriodConfig` is **optional** - commands without it default to `NONE` and work normally
- This is an **opt-in feature** - you must explicitly add `@PeriodConfig` to enable period segmentation
- Period segmentation is most beneficial for large event histories where querying all events becomes slow

See [Closing the Books Pattern Guide](../crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md) for complete documentation and examples.

## Metrics

CommandExecutor supports metrics collection via Spring's `ApplicationEventPublisher`:

- **Metrics are enabled by default**: Spring Boot automatically provides an `ApplicationEventPublisher` bean
- **Required parameter**: The `eventPublisher` parameter is required in the constructor
- **Automatic metrics collection**: See [crablet-metrics-micrometer](../crablet-metrics-micrometer/README.md) for automatic metrics collection

The following metrics are published:
- `CommandStartedMetric` - Command execution started
- `CommandSuccessMetric` - Command execution succeeded
- `CommandFailureMetric` - Command execution failed
- `IdempotentOperationMetric` - Idempotent operation detected

## Learn More

- **[EventStore README](../crablet-eventstore/README.md)** - Core event sourcing library
- **[DCB Explained](../crablet-eventstore/docs/DCB_AND_CRABLET.md)** - Detailed DCB explanation
- **[Command Patterns](../crablet-eventstore/docs/COMMAND_PATTERNS.md)** - Complete command pattern examples

## License

MIT

