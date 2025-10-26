# Getting Started with Crablet EventStore

This guide walks you through integrating Crablet EventStore into your application using the wallet domain as an example.

## Step 1: Add Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-eventstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Step 2: Set Up Database

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

2. Copy schema to `src/main/resources/db/migration/V1__initial_schema.sql`:
```sql
-- Copy from crablet-eventstore/src/test/resources/db/migration/V1__go_crablet_schema.sql
```

3. Configure database:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wallet_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.flyway.enabled=true
```

## Step 3: Define Domain Events

Create domain event records:

```java
package com.example.wallet.events;

import java.math.BigDecimal;

public sealed interface WalletEvent permits WalletOpened, DepositMade, WithdrawalMade {}

public record WalletOpened(String ownerId, BigDecimal initialBalance) implements WalletEvent {}
public record DepositMade(BigDecimal amount) implements WalletEvent {}
public record WithdrawalMade(BigDecimal amount) implements WalletEvent {}
```

## Step 4: Create State and Projector

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

import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.example.wallet.domain.WalletBalance;
import com.example.wallet.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WalletBalanceProjector implements StateProjector<WalletBalance> {
    
    private final ObjectMapper objectMapper;
    
    public WalletBalanceProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public WalletBalance project(List<StoredEvent> events) {
        return events.stream()
            .map(this::deserializeEvent)
            .reduce(WalletBalance.zero(), this::apply, (a, b) -> b);
    }
    
    private WalletEvent deserializeEvent(StoredEvent stored) {
        try {
            return switch (stored.type()) {
                case "WalletOpened" -> 
                    objectMapper.readValue(stored.data().toString(), WalletOpened.class);
                case "DepositMade" -> 
                    objectMapper.readValue(stored.data().toString(), DepositMade.class);
                case "WithdrawalMade" -> 
                    objectMapper.readValue(stored.data().toString(), WithdrawalMade.class);
                default -> throw new IllegalArgumentException("Unknown event type: " + stored.type());
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
    
    private WalletBalance apply(WalletBalance balance, WalletEvent event) {
        return switch (event) {
            case WalletOpened e -> new WalletBalance(e.initialBalance());
            case DepositMade e -> new WalletBalance(balance.amount().add(e.amount()));
            case WithdrawalMade e -> new WalletBalance(balance.amount().subtract(e.amount()));
        };
    }
}
```

## Step 5: Write Command Handler with DCB

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

import com.crablet.eventstore.commands.CommandHandler;
import com.crablet.eventstore.commands.CommandResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.query.*;
import com.crablet.eventstore.store.*;
import com.example.wallet.commands.WithdrawCommand;
import com.example.wallet.domain.WalletBalance;
import com.example.wallet.events.WithdrawalMade;
import com.example.wallet.projectors.WalletBalanceProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {
    
    private final EventStore eventStore;
    private final WalletBalanceProjector projector;
    private final ObjectMapper objectMapper;
    
    public WithdrawCommandHandler(
            EventStore eventStore, 
            WalletBalanceProjector projector,
            ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.projector = projector;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public CommandResult handle(WithdrawCommand command) {
        // Define decision model: which events affect withdrawal decision?
        Query decisionModel = QueryBuilder.create()
            .hasTag("wallet_id", command.walletId())
            .eventNames("WalletOpened", "DepositMade", "WithdrawalMade")
            .build();
        
        try {
            // 1. Project current balance with cursor
            ProjectionResult<WalletBalance> result = eventStore.project(
                decisionModel,
                Cursor.zero(),  // or pass cursor from request
                WalletBalance.class,
                List.of(projector)
            );
            
            WalletBalance balance = result.state();
            Cursor cursor = result.cursor();
            
            // 2. Business logic: check sufficient funds
            if (balance.amount().compareTo(command.amount()) < 0) {
                return CommandResult.emptyWithReason("Insufficient funds");
            }
            
            // 3. Create event
            WithdrawalMade event = new WithdrawalMade(command.amount());
            AppendEvent appendEvent = AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", command.walletId())
                .tag("withdrawal_id", command.withdrawalId())
                .data(objectMapper.valueToTree(event))
                .build();
            
            // 4. Build append condition with DCB conflict check + idempotency
            AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WithdrawalMade", "withdrawal_id", command.withdrawalId())
                .build();
            
            // 5. Append with both checks
            eventStore.appendIf(List.of(appendEvent), condition);
            
            return CommandResult.success(appendEvent);
            
        } catch (ConcurrencyException e) {
            // Balance changed since we read it - retry
            return handle(command);  // Recursive retry
        }
    }
}
```

## Step 6: Use in REST Controller

```java
package com.example.wallet.api;

import com.crablet.eventstore.commands.CommandResult;
import com.example.wallet.commands.WithdrawCommand;
import com.example.wallet.handlers.WithdrawCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    
    private final WithdrawCommandHandler withdrawHandler;
    
    public WalletController(WithdrawCommandHandler withdrawHandler) {
        this.withdrawHandler = withdrawHandler;
    }
    
    @PostMapping("/{walletId}/withdrawals")
    public ResponseEntity<?> withdraw(
            @PathVariable String walletId,
            @RequestParam String withdrawalId,
            @RequestParam BigDecimal amount) {
        
        WithdrawCommand command = new WithdrawCommand(walletId, withdrawalId, amount);
        CommandResult result = withdrawHandler.handle(command);
        
        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result.reason());
        }
    }
}
```

## Step 7: Test with Testcontainers

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
        var result = withdrawHandler.handle(command);
        
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

## Next Steps

- Read [DCB Pattern](docs/DCB_AND_CRABLET.md) for detailed explanation
- See [SCHEMA.md](SCHEMA.md) for database details
- Check [TESTING.md](TESTING.md) for testing patterns
- Review [README.md](README.md) for API reference

