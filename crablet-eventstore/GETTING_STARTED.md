# Getting Started with Crablet EventStore

This guide walks you through integrating Crablet EventStore into your application using the wallet domain as an example.

Add the dependency as shown in the [EventStore README](README.md#maven-coordinates).

## Step 1: Set Up Database

### Create Database Schema

See [SCHEMA.md](SCHEMA.md) for complete schema documentation.

Quick setup using Flyway:

1. Add Flyway dependency:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

2. Copy schema migrations to `src/main/resources/db/migration/`:
```bash
# Copy both migration files
cp crablet-eventstore/src/test/resources/db/migration/V1__eventstore_schema.sql src/main/resources/db/migration/
cp crablet-eventstore/src/test/resources/db/migration/V2__outbox_schema.sql src/main/resources/db/migration/
```

3. Configure database:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wallet_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.flyway.enabled=true
```

## Step 2: Define Domain Events

Create domain event records:

```java
package com.example.wallet.events;

import java.math.BigDecimal;

public sealed interface WalletEvent permits WalletOpened, DepositMade, WithdrawalMade {}

public record WalletOpened(String ownerId, BigDecimal initialBalance) implements WalletEvent {}
public record DepositMade(BigDecimal amount) implements WalletEvent {}
public record WithdrawalMade(BigDecimal amount) implements WalletEvent {}
```

## Step 3: Create State and Projector

Define wallet balance state:

```java
package com.example.wallet.domain;

import java.math.BigDecimal;

public record WalletBalance(BigDecimal amount) {
    public WalletBalance {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
    }
    
    public static WalletBalance zero() {
        return new WalletBalance(BigDecimal.ZERO);
    }
}
```

Create projector to build state from events:

```java
package com.example.wallet.projectors;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.example.wallet.domain.WalletBalance;
import com.example.wallet.events.*;

import java.util.List;

// Not a singleton - create instances as needed. This class is stateless and thread-safe.
public class WalletBalanceProjector implements StateProjector<WalletBalance> {
    
    @Override
    public String getId() {
        return "wallet-balance-projector";
    }
    
    @Override
    public List<String> getEventTypes() {
        return List.of("WalletOpened", "DepositMade", "WithdrawalMade");
    }
    
    @Override
    public WalletBalance getInitialState() {
        return new WalletBalance(0, false);
    }
    
    @Override
    public WalletBalance transition(WalletBalance currentState, StoredEvent event, EventDeserializer context) {
        // Deserialize event
        WalletEvent walletEvent = context.deserialize(event, WalletEvent.class);
        
        return switch (walletEvent) {
            case WalletOpened opened -> 
                new WalletBalance(opened.initialBalance(), true);
            case DepositMade deposit -> 
                new WalletBalance(deposit.newBalance(), true);
            case WithdrawalMade withdrawal -> 
                new WalletBalance(withdrawal.newBalance(), true);
            default -> currentState;
        };
    }
}
```

## Step 3.5: Create Command Interface with Jackson Annotations

Commands must be part of a `@JsonSubTypes` hierarchy for automatic type extraction:

```java
package com.example.wallet.commands;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "commandType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OpenWalletCommand.class, name = "open_wallet"),
        @JsonSubTypes.Type(value = DepositCommand.class, name = "deposit"),
        @JsonSubTypes.Type(value = WithdrawCommand.class, name = "withdraw")
})
public interface WalletCommand {
    String getWalletId();
}
```

Each command record implements this interface:

```java
public record OpenWalletCommand(String walletId, String owner, BigDecimal initialBalance) 
        implements WalletCommand {}
```

**Important:** The `name` in `@JsonSubTypes.Type` must match the command type used in your application. This is the single source of truth for command types.

## Step 4: Write Command Handler with DCB

Create command and handler:

```java
package com.example.wallet.commands;

import java.math.BigDecimal;

public record WithdrawCommand(
    String walletId,
    String withdrawalId,
    BigDecimal amount
) {}
```

```java
package com.example.wallet.handlers;

import com.crablet.eventstore.command.CommandHandler;
import com.crablet.eventstore.command.CommandResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.query.*;
import com.crablet.eventstore.store.*;
import com.example.wallet.commands.WithdrawCommand;
import com.example.wallet.domain.WalletBalance;
import com.example.wallet.events.WithdrawalMade;
import com.example.wallet.projectors.WalletBalanceProjector;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {
    
    public WithdrawCommandHandler() {
    }
    
    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // Define decision model: which events affect withdrawal decision?
        // This filters events to only those for this wallet
        Query decisionModel = QueryBuilder.create()
            .hasTag("wallet_id", command.walletId())  // Filter by wallet_id tag
            .eventNames("WalletOpened", "DepositMade", "WithdrawalMade")
            .build();
        
        // 1. Project current balance with cursor
        // Create projector instance inline (not a singleton, thread-safe)
        WalletBalanceProjector projector = new WalletBalanceProjector();
        ProjectionResult<WalletBalance> result = eventStore.project(
            decisionModel,
            Cursor.zero(),
            WalletBalance.class,
            List.of(projector)
        );
        
        WalletBalance balance = result.state();
        Cursor cursor = result.cursor();
        
        // 2. Business logic: check sufficient funds
        if (balance.amount().compareTo(command.amount()) < 0) {
            throw new InsufficientFundsException(command.walletId(), balance.amount(), command.amount());
        }
        
        // 3. Create event
        WithdrawalMade event = new WithdrawalMade(command.amount(), command.withdrawalId());
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", command.walletId())
                .tag("withdrawal_id", command.withdrawalId())
                .data(event)
                .build()
        );
        
        // 4. Build append condition with DCB cursor check
        // Note: No idempotency check - cursor advancement detects if operation already succeeded
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
            .build();
        
        return CommandResult.of(events, condition);
    }
}
```

## Step 5: Execute Commands

Use `CommandExecutor` to execute commands with transaction management:

```java
package com.example.wallet.service;

import com.crablet.eventstore.command.CommandExecutor;
import com.crablet.eventstore.command.ExecutionResult;
import com.example.wallet.commands.WithdrawCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WalletService {
    
    private final CommandExecutor commandExecutor;
    
    public WalletService(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }
    
    public ExecutionResult withdraw(String walletId, String withdrawalId, BigDecimal amount) {
        WithdrawCommand command = new WithdrawCommand(walletId, withdrawalId, amount);
        
        // CommandExecutor handles:
        // - Transaction management
        // - Command persistence (if enabled)
        // Note: On ConcurrencyException, application should implement retry logic
        // (e.g., using Resilience4j @Retry annotation)
        return commandExecutor.executeCommand(command);
    }
}
```

The `CommandExecutor` coordinates command execution:
1. Receives command
2. Extracts command type from JSON (`commandType` property)
3. Finds handler automatically (handlers are auto-discovered via Spring `@Component`)
4. Command type is extracted from handler's generic type parameter (`CommandHandler<T>`)
5. Executes handler within a transaction
6. Throws `ConcurrencyException` if conflict detected (application should implement retry)
7. Persists command and events atomically

**Automatic Handler Registration:**
- Handlers implementing `CommandHandler<T>` are auto-discovered by Spring
- Command type is automatically extracted from the handler's generic type parameter
- Uses reflection to read `@JsonSubTypes` annotation on the command interface

**Example handler registration:**
```java
@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {
    // Command type "withdraw" is automatically extracted from:
    // 1. Generic type parameter: CommandHandler<WithdrawCommand>
    // 2. @JsonSubTypes annotation on WalletCommand interface
    // 3. Entry matching WithdrawCommand.class with name="withdraw"
}
```

## Step 6: Test with Testcontainers

See [TESTING.md](TESTING.md) for complete testing guide.

Quick example:

```java
package com.example.wallet;

import com.crablet.eventstore.store.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class WalletIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private WithdrawCommandHandler withdrawHandler;
    
    @Test
    void testWithdrawal() {
        // Test your handler
        var command = new WithdrawCommand("wallet-123", "w-1", new BigDecimal("100"));
        var result = withdrawHandler.handle(eventStore, command);
        
        assertTrue(result.success());
    }
}
```

## Key Concepts

### Decision Model
The `Query` passed to `AppendConditionBuilder` that defines which events affect your business decision.

### DCB Conflict Check
`AppendCondition` checks if any events matching the decision model appeared AFTER the cursor. If yes, throws `ConcurrencyException`.

### Idempotency Check
`withIdempotencyCheck()` searches ALL events (ignores cursor) to prevent duplicate operations.

## Example Domains

Working examples are available in the test scope for reference:

- **Wallet Domain** (`com.crablet.examples.wallet`): Complete wallet implementation with deposits, withdrawals, and transfers
- **Course Subscriptions** (`com.crablet.examples.courses`): Course management with student subscriptions demonstrating multi-entity DCB patterns

These examples demonstrate real-world usage of DCB patterns and can serve as templates for your own implementations.

## Next Steps

- Read [DCB Explained](docs/DCB_AND_CRABLET.md) for detailed explanation
- See [SCHEMA.md](SCHEMA.md) for database details
- Check [TESTING.md](TESTING.md) for testing patterns
- Review [README.md](README.md) for API reference
- Explore example domains in test scope for complete working implementations

