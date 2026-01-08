# Testing with Crablet EventStore

This guide shows how to write tests for event sourcing applications using Crablet.

## Testing Strategy

Crablet follows a **testing pyramid** approach: start with fast unit tests to validate domain logic, then move to integration tests for DCB concurrency and database behavior.

### Testing Pyramid

```
        /\
       /  \  E2E Tests (few)
      /____\
     /      \  Integration Tests (some)
    /________\
   /          \  Unit Tests (many)
  /____________\
```

**Start with Unit Tests** → Validate domain logic quickly (milliseconds)
- Business rule validation
- State transitions
- Balance calculations
- Event generation logic

**Then Integration Tests** → Validate DCB concurrency and database behavior (seconds)
- DCB concurrency conflicts
- Database constraints and transactions
- Real database behavior
- End-to-end flows

### Testing Workflow

1. **Write Unit Tests First** (Fast feedback during development)
   - Validate domain rules
   - Test happy paths and error cases
   - Catch logic errors quickly

2. **Write Integration Tests Second** (Validate system behavior)
   - Test concurrency scenarios
   - Test database constraints
   - Test transaction boundaries
   - Test idempotency with real database

## Pure Unit Testing for Command Handlers

Pure unit tests validate business logic in isolation without database dependencies. They use an in-memory `EventStore` implementation that stores events directly (no JSON serialization) and uses real projection logic.

### When to Use Unit Tests

**Use unit tests for:**
- Happy path scenarios
- Business rule validation
- Balance calculations, state transitions
- Event generation logic
- Domain exception handling

**Use integration tests instead for:**
- DCB concurrency conflicts
- Database constraints
- Transaction boundaries
- Idempotency with real database

### Infrastructure Components

#### InMemoryEventStore

**Location:** `com.crablet.command.handlers.unit.InMemoryEventStore`

In-memory `EventStore` implementation for unit testing:
- Stores original event objects directly (no JSON serialization)
- Uses real `StateProjector` logic for accurate projections
- Accepts all appends (no DCB concurrency checks for unit tests)
- Fast and lightweight - no database overhead

#### AbstractHandlerUnitTest

**Location:** `com.crablet.command.handlers.unit.AbstractHandlerUnitTest`

BDD-style base class providing:
- `given()` - Builder callback pattern for event seeding
- `when()` - Execute handler and extract pure domain events (returns `List<Object>`)
- `whenWithTags()` - Execute handler and get events with tags (returns `List<EventWithTags<Object>>`)
- `then()` - Assert on single event with type extraction
- `thenMultipleOrdered()` - Assert on multiple events with count and order using pattern matching
- `thenMultipleWithTagsOrdered()` - Assert on multiple events with tags, count and order using pattern matching
- `at()` - Helper method for indexed access to events in lists
- Pattern matching with sealed interfaces (no casts, no default clauses needed)
- Domain-agnostic and reusable

#### Domain-Specific Factories

**Example:** `com.crablet.command.handlers.wallet.unit.WalletPeriodHelperTestFactory`

Factory for creating test-friendly domain helpers:
- Simplified period resolution (fixed period, no clock dependency)
- Works with `InMemoryEventStore`
- **Note:** This is domain-specific. For other domains, create your own factory following the same pattern.

### Quick Start

Extend `AbstractHandlerUnitTest` and start writing tests:

```java
package com.example.wallet.unit;

import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import com.crablet.command.handlers.wallet.DepositCommandHandler;
import com.crablet.examples.wallet.event.DepositMade;
import com.crablet.examples.wallet.event.WalletOpened;
import com.crablet.examples.wallet.features.deposit.DepositCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DepositCommandHandler Unit Tests")
class DepositCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private DepositCommandHandler handler;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        // Initialize handler with domain-specific helpers if needed
        handler = new DepositCommandHandler(/* ... */);
    }
    
    @Test
    @DisplayName("Given wallet with balance, when depositing, then balance increases")
    void givenWalletWithBalance_whenDepositing_thenBalanceIncreases() {
        // Given
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When
        DepositCommand command = DepositCommand.of("deposit1", "wallet1", 500, "Bonus");
        List<Object> events = when(handler, command);
        
        // Then
        then(events, DepositMade.class, deposit -> {
            assertThat(deposit.walletId()).isEqualTo("wallet1");
            assertThat(deposit.amount()).isEqualTo(500);
            assertThat(deposit.newBalance()).isEqualTo(1500); // 1000 + 500
        });
    }
}
```

**Reference:** See `DepositCommandHandlerUnitTest` for complete examples.

### Unit Test Examples

#### Basic Example: Balance Calculation

```java
@Test
@DisplayName("Given wallet with previous deposits, when depositing, then balance accumulates correctly")
void givenWalletWithPreviousDeposits_whenDepositing_thenBalanceAccumulatesCorrectly() {
    // Given
    given().event(type(WalletOpened.class), builder -> builder
        .data(WalletOpened.of("wallet1", "Alice", 1000))
        .tag(WALLET_ID, "wallet1")
    );
    given().event(type(DepositMade.class), builder -> builder
        .data(DepositMade.of("deposit1", "wallet1", 200, 1200, "First deposit"))
        .tag(WALLET_ID, "wallet1")
    );
    
    // When
    DepositCommand command = DepositCommand.of("deposit2", "wallet1", 300, "Second deposit");
    List<Object> events = when(handler, command);
    
    // Then
    then(events, DepositMade.class, deposit -> {
        assertThat(deposit.newBalance()).isEqualTo(1500); // 1000 + 200 + 300
    });
}
```

**Reference:** `DepositCommandHandlerUnitTest.givenWalletWithPreviousDeposits_whenDepositing_thenBalanceAccumulatesCorrectly()`

#### Period Test Example: Tag Assertions

```java
@Test
@DisplayName("Given wallet, when depositing, then deposit has correct period tags")
void givenWallet_whenDepositing_thenDepositHasCorrectPeriodTags() {
    // Given
    given().event(type(WalletOpened.class), builder -> builder
        .data(WalletOpened.of("wallet1", "Alice", 1000))
        .tag(WALLET_ID, "wallet1")
    );
    
    // When - get events with tags
    DepositCommand command = DepositCommand.of("deposit1", "wallet1", 500, "Bonus");
    List<EventWithTags<Object>> events = whenWithTags(handler, command);
    
    // Then - verify business logic AND period tags
    then(events, DepositMade.class, (deposit, tags) -> {
        // Business logic
        assertThat(deposit.newBalance()).isEqualTo(1500);
        
        // Period tags
        assertThat(tags).containsEntry("wallet_id", "wallet1");
        assertThat(tags).containsEntry("year", "2025");
        assertThat(tags).containsEntry("month", "1");
    });
}
```

**Reference:** `DepositCommandHandlerUnitTest.givenWallet_whenDepositing_thenDepositHasCorrectPeriodTags()`

#### Error Case Example: Exception Testing

```java
@Test
@DisplayName("Given wallet with insufficient balance, when withdrawing, then insufficient funds exception")
void givenWalletWithInsufficientBalance_whenWithdrawing_thenInsufficientFundsException() {
    // Given
    given().event(type(WalletOpened.class), builder -> builder
        .data(WalletOpened.of("wallet1", "Alice", 100))
        .tag(WALLET_ID, "wallet1")
    );
    
    // When & Then
    WithdrawCommand command = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Shopping");
    assertThatThrownBy(() -> when(handler, command))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("wallet1");
}
```

**Reference:** `WithdrawCommandHandlerUnitTest.givenWalletWithInsufficientBalance_whenWithdrawing_thenInsufficientFundsException()`

#### Multiple Events with Order Assertions

When a handler generates multiple events, use `thenMultipleOrdered()` to assert both count and order using pattern matching:

```java
@Test
@DisplayName("Given wallet, when depositing, then multiple events generated in correct order")
void givenWallet_whenDepositing_thenMultipleEventsGeneratedInCorrectOrder() {
    // Given
    given().event(type(WalletOpened.class), builder -> builder
        .data(WalletOpened.of("wallet1", "Alice", 1000))
        .tag(WALLET_ID, "wallet1")
    );
    
    // When
    DepositCommand command = DepositCommand.of("deposit1", "wallet1", 500, "Bonus");
    List<Object> events = when(handler, command);
    
    // Then - assert count and order using pattern matching
    thenMultipleOrdered(events, WalletEvent.class, 2, walletEvents -> {
        switch (at(0, walletEvents)) {
            case DepositMade deposit -> {
                assertThat(deposit.amount()).isEqualTo(500);
                assertThat(deposit.newBalance()).isEqualTo(1500);
            }
        }
        switch (at(1, walletEvents)) {
            case WalletStatementOpened statement -> {
                assertThat(statement.openingBalance()).isEqualTo(1000);
            }
        }
    });
}
```

**Key features:**
- `thenMultipleOrdered()` asserts both count and order
- `at(index, list)` helper provides indexed access for order assertions
- Pattern matching with sealed interfaces (no casts, no default clause needed)
- Type-safe and compiler-enforced exhaustiveness

**Reference:** See `DepositCommandHandlerUnitTest` for complete examples.

#### Multiple Events with Tags and Order Assertions

For period tests with multiple events, use `thenMultipleWithTagsOrdered()`:

```java
@Test
@DisplayName("Given wallet, when depositing, then multiple events with tags in correct order")
void givenWallet_whenDepositing_thenMultipleEventsWithTagsInCorrectOrder() {
    // Given
    given().eventWithMonthlyPeriod(...);
    
    // When
    List<EventWithTags<Object>> events = whenWithTags(handler, command);
    
    // Then - assert count, order, and tags using pattern matching
    thenMultipleWithTagsOrdered(events, WalletEvent.class, 2, eventWithTagsList -> {
        switch (at(0, eventWithTagsList).event()) {
            case DepositMade deposit -> {
                assertThat(deposit.amount()).isEqualTo(500);
                assertThat(at(0, eventWithTagsList).tags()).containsEntry("year", "2025");
            }
        }
        switch (at(1, eventWithTagsList).event()) {
            case WalletStatementOpened statement -> {
                assertThat(statement.openingBalance()).isEqualTo(1000);
                assertThat(at(1, eventWithTagsList).tags()).containsEntry("statement_id", "...");
            }
        }
    });
}
```

**Reference:** See `DepositCommandHandlerUnitTest` for complete examples.

### Performance Benefits

**Unit Tests:**
- Run in **milliseconds** (typically < 10ms per test)
- No database startup overhead
- No network latency
- No serialization/deserialization overhead
- Perfect for TDD and fast feedback loops

**Integration Tests:**
- Run in **seconds** (typically 100-500ms per test)
- Database container startup (one-time cost)
- Real database operations
- Network overhead
- Serialization/deserialization

### Test Coverage Complementarity

**Unit Tests Cover:**
- Business logic validation
- State transitions
- Balance calculations
- Event generation logic
- Error handling (domain exceptions)

**Integration Tests Cover:**
- DCB concurrency conflicts
- Database constraints
- Transaction boundaries
- Idempotency with real database
- End-to-end flows

**Together:** Comprehensive coverage without gaps
- Unit tests catch logic errors quickly
- Integration tests catch concurrency and database issues

### Troubleshooting

**Common Issues:**

- **Period helper not found**: Create domain-specific factory (see `WalletPeriodHelperTestFactory` as example)
- **Tag assertions failing**: Use `whenWithTags()` instead of `when()` to get events with tags
- **Projection not working**: Ensure events are seeded with correct tags matching your query patterns
- **Handler dependencies**: Override `setUp()` to initialize domain-specific helpers (e.g., `WalletPeriodHelper`)

**Best Practices:**

- Keep unit tests focused on single scenarios
- Use descriptive test names following Given-When-Then pattern
- Assert on pure domain events, not framework wrappers
- Use `whenWithTags()` only when you need to verify tags (e.g., period tests)

## Integration Testing with Testcontainers

Integration tests validate DCB concurrency, database constraints, and real database behavior. They use Testcontainers to spin up a real PostgreSQL database.

### When to Use Integration Tests

**Use integration tests for:**
- DCB concurrency conflicts
- Database constraints and transactions
- Real database behavior
- End-to-end flows
- Idempotency with real database

**Note:** Write unit tests first to validate domain logic quickly, then add integration tests for system-level concerns.

### Testcontainers Setup

#### Add Dependencies

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

#### Base Test Class

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

### Integration Test Example

#### Testing Command Handler

```java
package com.example.wallet.handlers;

import com.crablet.command.CommandResult;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.eventstore.query.EventRepository;
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
    private EventRepository eventRepository;  // Test-only repository
    
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
        
        List<StoredEvent> events = eventRepository.query(query, Cursor.zero());
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
        
        List<StoredEvent> events = eventRepository.query(query, Cursor.zero());
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

### Testing DCB Conflicts

#### Concurrent Modifications

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

## Quick Reference

| Aspect | Unit Tests | Integration Tests |
|--------|------------|-------------------|
| **Speed** | Fast (< 10ms) | Slower (100-500ms) |
| **Dependencies** | None | Database (Testcontainers) |
| **Focus** | Business Logic | DCB + Database |
| **When to Use** | Development/TDD | Concurrency/Constraints |
| **Coverage** | Domain Rules | System Behavior |

## EventRepository

EventRepository provides direct access to raw events, bypassing the DCB pattern.
It is optional and free for use anywhere in your application.

**Note:** For use cases requiring DCB concurrency control, use `EventStore.project()` instead.

```java
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.StoredEvent;

@Autowired
private EventRepository eventRepository;

@Test
void testEventStorage() {
    // Query events directly (bypasses projections)
    Query query = QueryBuilder.create()
        .hasTag("wallet_id", walletId)
        .build();
    
    List<StoredEvent> events = eventRepository.query(query, Cursor.zero());
    
    // Assert on raw events
    assertEquals(3, events.size());
    assertEquals("WalletOpened", events.get(0).type());
}
```

### When to Use EventRepository

**Use for:**
- Verifying events were stored
- Checking event order
- Inspecting event tags
- Debugging
- Migration scripts
- Any use case where you need direct access to raw events

**Use EventStore.project() instead for:**
- Command handlers (requires DCB concurrency control)
- Business logic requiring state projection
- Use cases where you need concurrency control

## Assertions

### Event Assertions

```java
import static org.junit.jupiter.api.Assertions.*;

// Assert event stored
List<StoredEvent> events = eventRepository.query(query, Cursor.zero());
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

- **Unit Tests** (`com.crablet.command.handlers.*.unit.*`): Pure unit tests for business logic
  - Fast, isolated, no database dependencies
  - Use `AbstractHandlerUnitTest` base class
  - Focus on domain rules and state transitions

- **Integration Tests** (`com.crablet.command.handlers.*.integration.*`): Integration tests with Testcontainers
  - Real database testing
  - DCB concurrency validation
  - Use `AbstractCrabletTest` base class

- **Framework Tests** (`com.crablet.eventstore.integration.*`): Integration tests for core EventStore functionality
  - Base test class: `AbstractCrabletTest`
  - Test application: `TestApplication`

- **Example Domains** (`com.crablet.examples.*`): Complete working examples demonstrating DCB patterns
  - `com.crablet.examples.wallet`: Wallet domain with deposits, withdrawals, transfers
  - `com.crablet.examples.course`: Course subscriptions with multi-entity constraints
  - Each example domain includes:
    - Domain events, commands, handlers
    - Projectors and query patterns
    - Unit tests (in `*.unit.*` package)
    - Integration tests (in `*.integration.*` package)

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
