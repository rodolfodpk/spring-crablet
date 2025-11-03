# Testing with Crablet EventStore

This guide shows how to write tests for event sourcing applications using Crablet.

## Testcontainers Setup

### Add Dependencies

```xml
<dependencies>
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Testcontainers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Base Test Class

Use the framework's base test class for integration tests:

```java
package com.example.wallet;

import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.eventstore.store.EventStore;
import org.springframework.beans.factory.annotation.Autowired;

// AbstractCrabletTest provides:
// - Shared PostgreSQL Testcontainers container
// - Automatic Flyway migrations
// - EventStore and JdbcTemplate autowired
public class WalletIntegrationTest extends AbstractCrabletTest {
    
    @Autowired
    protected EventStore eventStore;
    
    // Your test methods here
}
```

Alternatively, create your own base test class following the same pattern. See `com.crablet.eventstore.integration.AbstractCrabletTest` for reference.

## Integration Test Example

### Testing Command Handler

```java
package com.example.wallet.handlers;

import com.crablet.command.CommandResult;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.eventstore.query.EventTestHelper;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.example.wallet.commands.WithdrawCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WithdrawCommandHandlerTest extends AbstractCrabletTest {
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private WithdrawCommandHandler handler;
    
    @Autowired
    private EventTestHelper eventTestHelper;  // Test-only helper
    
    @Test
    void testSuccessfulWithdrawal() {
        // Given: wallet with balance
        String walletId = "wallet-" + System.currentTimeMillis();
        String withdrawalId = "withdrawal-1";
        
        // When: withdraw money
        WithdrawCommand command = new WithdrawCommand(
            walletId, 
            withdrawalId, 
            new BigDecimal("50")
        );
        CommandResult result = handler.handle(eventStore, command);
        
        // Then: withdrawal succeeded
        assertTrue(result.success());
        assertFalse(result.events().isEmpty());
        
        // Verify event was stored
        Query query = QueryBuilder.create()
            .hasTag("wallet_id", walletId)
            .eventNames("WithdrawalMade")
            .build();
        
        List<StoredEvent> events = eventTestHelper.query(query, Cursor.zero());
        assertEquals(1, events.size());
        assertEquals("WithdrawalMade", events.get(0).type());
        assertTrue(events.get(0).tags().contains("withdrawal_id:" + withdrawalId));
    }
    
    @Test
    void testIdempotentWithdrawal() {
        // Given: wallet
        String walletId = "wallet-" + System.currentTimeMillis();
        String withdrawalId = "withdrawal-duplicate";
        
        WithdrawCommand command = new WithdrawCommand(
            walletId,
            withdrawalId,
            new BigDecimal("50")
        );
        
        // When: withdraw twice with same ID
        CommandResult result1 = handler.handle(eventStore, command);
        CommandResult result2 = handler.handle(eventStore, command);
        
        // Then: both succeed (idempotent)
        assertTrue(result1.success());
        assertTrue(result2.success());
        
        // But only one event stored
        Query query = QueryBuilder.create()
            .hasTag("wallet_id", walletId)
            .hasTag("withdrawal_id", withdrawalId)
            .build();
        
        List<StoredEvent> events = eventTestHelper.query(query, Cursor.zero());
        assertEquals(1, events.size(), "Should only store one withdrawal event");
    }
    
    @Test
    void testInsufficientFunds() {
        // Given: wallet with low balance
        String walletId = "wallet-" + System.currentTimeMillis();
        
        // Setup wallet with $100
        setupWalletWithBalance(walletId, new BigDecimal("100"));
        
        // When: try to withdraw $200
        WithdrawCommand command = new WithdrawCommand(
            walletId,
            "withdrawal-insufficient",
            new BigDecimal("200")
        );
        CommandResult result = handler.handle(eventStore, command);
        
        // Then: withdrawal failed (handler throws exception, not returns failure)
        // Handler will throw InsufficientFundsException
        assertThrows(InsufficientFundsException.class, 
            () -> handler.handle(eventStore, command));
    }
    
    private void setupWalletWithBalance(String walletId, BigDecimal balance) {
        // Helper to setup test wallet with initial balance
        // Implementation depends on your OpenWallet and Deposit handlers
    }
}
```

## Unit Test Example

### Testing State Projector

```java
package com.example.wallet.projectors;

import com.crablet.eventstore.store.StoredEvent;
import com.example.wallet.domain.WalletBalance;
import com.example.wallet.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WalletBalanceProjectorTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WalletBalanceProjector projector = new WalletBalanceProjector(objectMapper);
    
    @Test
    void testProjectBalance() {
        // Given: events
        List<StoredEvent> events = List.of(
            storedEvent("WalletOpened", new WalletOpened("user-1", new BigDecimal("1000")), 1L),
            storedEvent("DepositMade", new DepositMade(new BigDecimal("500")), 2L),
            storedEvent("WithdrawalMade", new WithdrawalMade(new BigDecimal("300")), 3L)
        );
        
        // When: project balance
        WalletBalance balance = projector.project(events);
        
        // Then: correct balance
        assertEquals(new BigDecimal("1200"), balance.amount());
    }
    
    @Test
    void testProjectEmptyEvents() {
        WalletBalance balance = projector.project(List.of());
        assertEquals(BigDecimal.ZERO, balance.amount());
    }
    
    private StoredEvent storedEvent(String type, Object data, long position) {
        return new StoredEvent(
            position,
            type,
            List.of("wallet_id:test"),
            objectMapper.valueToTree(data),
            "0",
            Instant.now()
        );
    }
}
```

## EventTestHelper

**Important:** EventTestHelper is for tests only. It bypasses DCB.

```java
import com.crablet.eventstore.query.EventTestHelper;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.StoredEvent;

@Autowired
private EventTestHelper eventTestHelper;

@Test
void testEventStorage() {
    // Query events directly (bypasses projections)
    Query query = QueryBuilder.create()
        .hasTag("wallet_id", walletId)
        .build();
    
    List<StoredEvent> events = eventTestHelper.query(query, Cursor.zero());
    
    // Assert on raw events
    assertEquals(3, events.size());
    assertEquals("WalletOpened", events.get(0).type());
}
```

### When to Use EventTestHelper

**Use for:**
- Verifying events were stored
- Checking event order
- Inspecting event tags
- Debugging tests

**Don't use for:**
- Production code
- Command handlers (use EventStore.project instead)
- Business logic

## Testing DCB Conflicts

### Concurrent Modifications

```java
@Test
void testConcurrentWithdrawals() {
    String walletId = "wallet-concurrent";
    setupWalletWithBalance(walletId, new BigDecimal("100"));
    
    // Simulate concurrent withdrawals
    WithdrawCommand command1 = new WithdrawCommand(walletId, "w-1", new BigDecimal("80"));
    WithdrawCommand command2 = new WithdrawCommand(walletId, "w-2", new BigDecimal("80"));
    
    // First withdrawal succeeds
    CommandResult result1 = handler.handle(eventStore, command1);
    assertTrue(result1.success());
    
    // Second withdrawal fails (insufficient funds after first)
    assertThrows(InsufficientFundsException.class, 
        () -> handler.handle(eventStore, command2));
}
```

## Assertions

### Event Assertions

```java
import static org.junit.jupiter.api.Assertions.*;

// Assert event stored
List<StoredEvent> events = eventTestHelper.query(query, Cursor.zero());
assertFalse(events.isEmpty(), "Events should be stored");

// Assert event type
assertEquals("WithdrawalMade", events.get(0).type());

// Assert tags
assertTrue(events.get(0).tags().contains("wallet_id:123"));
assertTrue(events.get(0).tags().contains("withdrawal_id:w-1"));

// Assert event count
assertEquals(3, events.size(), "Should have 3 events");

// Assert event order
assertEquals("WalletOpened", events.get(0).type());
assertEquals("DepositMade", events.get(1).type());
assertEquals("WithdrawalMade", events.get(2).type());
```

### State Assertions

```java
// Assert projected state
WalletBalance balance = projector.project(events);
assertEquals(new BigDecimal("1200"), balance.amount());
assertTrue(balance.amount().compareTo(BigDecimal.ZERO) > 0);
```

## Best Practices

### 1. Use Unique IDs in Tests

```java
String walletId = "wallet-" + System.currentTimeMillis();
String withdrawalId = UUID.randomUUID().toString();
```

### 2. Test Idempotency

Always test duplicate operations:

```java
CommandResult result1 = handler.handle(eventStore, command);
CommandResult result2 = handler.handle(eventStore, command);  // Same command

assertTrue(result1.success());
assertTrue(result2.success());  // Should also succeed (idempotent)
```

### 3. Test Error Cases

```java
@Test
void testInvalidAmount() {
    WithdrawCommand command = new WithdrawCommand(
        walletId, 
        withdrawalId, 
        new BigDecimal("-10")  // Negative amount
    );
    
    assertThrows(IllegalArgumentException.class, 
        () -> handler.handle(eventStore, command));
}
```

### 4. Clean Test Data

Use unique IDs per test to avoid conflicts:

```java
@BeforeEach
void setUp() {
    walletId = "wallet-test-" + System.nanoTime();
}
```

### 5. Test DCB Scenarios

Always test:
- Normal case (no conflicts)
- Concurrent modifications (conflicts)
- Duplicate operations (idempotency)
- Insufficient balance
- Invalid input

## Test Structure

The test scope is organized into:

- **Framework Tests** (`com.crablet.eventstore.integration.*`): Integration tests for core EventStore functionality
  - Base test class: `AbstractCrabletTest`
  - Test application: `TestApplication`
- **Example Domains** (`com.crablet.examples.*`): Complete working examples demonstrating DCB patterns
  - `com.crablet.examples.wallet`: Wallet domain with deposits, withdrawals, transfers
  - `com.crablet.examples.courses`: Course subscriptions with multi-entity constraints
  - Each example domain includes:
    - Domain events, commands, handlers
    - Projectors and query patterns
    - Integration tests
    - Test utilities (in `*.testutils` package):
      - `com.crablet.examples.wallet.testutils.WalletTestUtils`
      - `com.crablet.examples.courses.testutils.CourseTestUtils`

These example domains serve as reference implementations and can be used as templates for your own domains.

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=WithdrawCommandHandlerTest

# Run with Testcontainers debug
mvn test -Dtest=WithdrawCommandHandlerTest -Dtestcontainers.reuse.enable=true
```

## Next Steps

- Read [GETTING_STARTED.md](GETTING_STARTED.md) for integration examples
- See [DCB Explained](docs/DCB_AND_CRABLET.md) for DCB concepts
- Check [SCHEMA.md](SCHEMA.md) for database details

