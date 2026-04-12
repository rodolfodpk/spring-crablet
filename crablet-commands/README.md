# Crablet Command Framework

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_command)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light command handling framework for event sourcing with automatic handler discovery and Spring Boot integration.

## Recommended Adoption Path

`crablet-commands` should be the default first adoption path for Crablet.

Start with:

- `crablet-eventstore`
- `crablet-commands`

Add `views`, `outbox`, and `automations` later, after the write path is working and the domain model is stable.

This module is also the natural foundation for a future command-side starter. The goal of that starter should be narrow: command execution, handler discovery, and default wiring. It should not hide the deployment implications of poller-backed modules.

## Start Here

- If you are new to Crablet, read `Quick Start` first
- Pair this README with [../crablet-eventstore/GETTING_STARTED.md](../crablet-eventstore/GETTING_STARTED.md)
- Add views, automations, and outbox only after command execution is working cleanly

## Overview

Crablet Command provides a lightweight framework for command handling on top of Crablet EventStore:

- **Automatic Handler Discovery**: Handlers are auto-discovered via Spring `@Component` annotation
- **Type-Safe Commands**: Command handler with automatic projection
- **Type Extraction**: Command types extracted from handler's generic type parameter
- **Transaction Management**: Automatic transaction lifecycle management
- **DCB Support**: Full support for Dynamic Consistency Boundary pattern

For a commands-first adoption guide, see [../docs/COMMANDS_FIRST_ADOPTION.md](../docs/COMMANDS_FIRST_ADOPTION.md).

**Light framework shape:**
- Implement one `CommandHandler<T>` per command type
- Inject `CommandExecutor` and `EventStore`
- Keep command orchestration explicit instead of hidden behind conventions

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
    <artifactId>crablet-commands</artifactId>
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

### Test Dependencies

For unit and integration testing, add `crablet-test-support`:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-test-support</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

This provides:
- **InMemoryEventStore** - Fast in-memory event store for unit tests
- **AbstractCrabletTest** - Base class for integration tests with Testcontainers
- **AbstractHandlerUnitTest** - BDD-style base class for command handler unit tests

See [EventStore TESTING.md](../crablet-eventstore/TESTING.md) for complete testing guide.

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
public class DepositCommandHandler implements CommutativeCommandHandler<DepositCommand> {
    @Override
    public CommandDecision.CommutativeDecision decide(EventStore eventStore, DepositCommand command) {
        // 1. Project lifecycle state
        Query lifecycleModel = WalletQueryPatterns.walletLifecycleModel(command.walletId());
        ProjectionResult<WalletBalanceState> projection = eventStore.project(lifecycleModel, balanceProjector);

        // 2. Validate business rules
        if (!projection.state().isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }

        // 3. Create event
        DepositMade depositMade = DepositMade.of(...);
        AppendEvent event = AppendEvent.builder("DepositMade")
                .tag("wallet_id", command.walletId())
                .tag("deposit_id", command.depositId())
                .data(depositMade)
                .build();

        // 4. CommutativeGuarded: concurrent deposits are still allowed, but a concurrent
        //    WalletClosed event (in the guard query) will trigger a ConcurrencyException.
        //    The guard query must contain only lifecycle event types — NOT DepositMade —
        //    so parallel deposits do not block each other.
        Query lifecycleGuard = WalletQueryPatterns.walletLifecycleModel(command.walletId());
        return CommandDecision.CommutativeGuarded.withLifecycleGuard(event, lifecycleGuard, projection.streamPosition());
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
        return CommandExecutors.create(
                eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }
}
```

At the moment, explicit bean wiring is the supported setup for `CommandExecutor`.
Use the public `CommandExecutors.create(...)` factory rather than instantiating
the internal executor implementation directly.

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
        return commandExecutor.execute(command);
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
    // 1. Handler queries/projects state and returns CommandDecision (uses same transaction)
    CommandDecision decision = handler.handle(txStore, command);
    
    // 2. Append events via the correct semantic method based on decision type
    //    (uses same transaction, returns transactionId)
    String transactionId = switch (decision) {
        case CommandDecision.Commutative c     -> txStore.appendCommutative(c.events());
        case CommandDecision.CommutativeGuarded cg -> {
            // Lifecycle guard: detect if entity state changed between projection and append
            // without blocking concurrent commutative operations of the same type
            if (txStore.project(cg.guardQuery(), cg.guardPosition(), StateProjector.exists()).state()) throw new ConcurrencyException(...);
            yield txStore.appendCommutative(cg.events());
        }
        case CommandDecision.NonCommutative nc -> txStore.appendNonCommutative(nc.events(), nc.decisionModel(), nc.streamPosition());
        case CommandDecision.Idempotent i      -> txStore.appendIdempotent(i.events(), i.eventType(), i.tagKey(), i.tagValue());
        case CommandDecision.NoOp noOp         -> null; // nothing to append
    };
    
    // 3. Store command (uses same transaction, uses transactionId from step 2)
    if (transactionId != null && txStore instanceof CommandAuditStore auditStore)
        auditStore.storeCommand(commandJson, commandType, transactionId);
    
    // All operations commit atomically, or all rollback on error
});
```

### How It Works

1. **Transaction Start**: `executeInTransaction()` creates a single `Connection` with `autoCommit(false)`
2. **ConnectionScopedEventStore**: All operations receive a `ConnectionScopedEventStore` that uses the same `Connection`
3. **Handler Queries**: When handler calls `txStore.project()`, it uses `projectWithConnection(connection, ...)` - same transaction
4. **Event Append**: When the append method is called, it uses `appendIfWithConnection(connection, ...)` internally - same transaction
5. **Command Storage**: `txStore` is cast to `CommandAuditStore`; `storeCommand()` uses `storeCommandWithConnection(connection, ...)` — same transaction
6. **Atomic Commit**: All operations commit together, or all rollback on any error

### Transaction ID

The `transactionId` is generated by PostgreSQL's `pg_current_xact_id()` function when events are appended:

```sql
-- Inside append_events_if function:
'transaction_id', pg_current_xact_id()::TEXT
```

**Important points:**
- ✅ **Same transaction ID**: All operations (queries, append, command storage) use the same transaction ID
- ✅ **Generated on append**: The transactionId is only available after the append method returns it
- ✅ **Represents entire transaction**: `pg_current_xact_id()` returns the ID of the current transaction, which is the same for all operations in that transaction

**Timeline:**
1. Handler queries state → Uses transaction (transactionId exists but not yet read)
2. Handler calls append method → PostgreSQL function returns transactionId via `pg_current_xact_id()`
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
- DCB concurrency checks work correctly (stream position is consistent with appended events)

## Command Patterns

Crablet Command supports three DCB patterns via typed sub-interfaces:

- **`IdempotentCommandHandler`**: entity creation with duplicate guard (`appendIdempotent`)
- **`NonCommutativeCommandHandler`**: state-dependent operations with stream-position conflict check (`appendNonCommutative`)
- **`CommutativeCommandHandler`**: order-independent operations (`appendCommutative`); can return either:
  - `CommandDecision.Commutative` — no conflict detection (truly state-independent events)
  - `CommandDecision.CommutativeGuarded` — concurrent same-type operations are still allowed, but a lifecycle guard query is checked atomically before appending; use this when the entity must be active (e.g., wallet must be open to receive a deposit)

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
public class DepositCommandHandler implements CommutativeCommandHandler<DepositCommand> {
    private final WalletPeriodHelper periodHelper; // Domain-specific helper

    @Override
    public CommandDecision decide(EventStore eventStore, DepositCommand command) {
        // Project balance for current period (period tags derived from clock, no statement creation)
        var periodResult = periodHelper.projectCurrentPeriod(
            eventStore, command.walletId(), DepositCommand.class);

        var state = periodResult.projection().state();

        // Validate wallet exists and is open
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
            .tag("year", periodId.year())
            .tag("month", periodId.month());

        // Add day/hour tags conditionally — only present for DAILY/HOURLY granularity
        if (periodId.day() != null)  eventBuilder.tag("day", periodId.day());
        if (periodId.hour() != null) eventBuilder.tag("hour", periodId.hour());

        AppendEvent event = eventBuilder.data(deposit).build();

        // CommutativeGuarded: concurrent deposits are still allowed, but a concurrent
        // WalletClosed event detected by the guard will trigger a ConcurrencyException.
        Query lifecycleGuard = WalletQueryPatterns.walletLifecycleModel(command.walletId());
        return CommandDecision.CommutativeGuarded.withLifecycleGuard(event, lifecycleGuard, periodResult.projection().streamPosition());
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
