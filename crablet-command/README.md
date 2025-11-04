# Crablet Command Framework

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
- Spring Boot Web, JDBC
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
            EventStoreMetrics metrics,
            ObjectMapper objectMapper) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, metrics, objectMapper);
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

## Command Patterns

Crablet Command supports different DCB patterns:

- **Idempotency Check**: `withIdempotencyCheck()` for entity creation
- **Cursor-based Check**: `AppendConditionBuilder(decisionModel, cursor)` for state-dependent operations
- **Empty Condition**: `AppendCondition.empty()` for commutative operations

See [Command Patterns Guide](../crablet-eventstore/docs/COMMAND_PATTERNS.md) for complete examples.

## Learn More

- **[EventStore README](../crablet-eventstore/README.md)** - Core event sourcing library
- **[DCB Explained](../crablet-eventstore/docs/DCB_AND_CRABLET.md)** - Detailed DCB explanation
- **[Command Patterns](../crablet-eventstore/docs/COMMAND_PATTERNS.md)** - Complete command pattern examples

## License

MIT

