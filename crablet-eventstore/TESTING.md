# Testing with Crablet EventStore

## Quick Reference

**Testing Strategy in 2 minutes:**

1. **Unit Tests First** (fast, < 10ms) → Validate domain logic, business rules, state transitions
2. **Integration Tests Second** (slower, 100-500ms) → Validate DCB concurrency, database constraints

**Quick Comparison:**

| Aspect | Unit Tests | Integration Tests |
|--------|------------|-------------------|
| **Speed** | Fast (< 10ms) | Slower (100-500ms) |
| **Dependencies** | None | Database (Testcontainers) |
| **Focus** | Business Logic | DCB + Database |
| **When to Use** | Development/TDD | Concurrency/Constraints |
| **Coverage** | Domain Rules | System Behavior |

📖 **Details:** See [sections below](#testing-strategy).

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
- Business rule validation, state transitions, balance calculations, event generation logic

**Then Integration Tests** → Validate DCB concurrency and database behavior (seconds)
- DCB concurrency conflicts, database constraints, transaction boundaries, idempotency

### Testing Workflow

1. **Write Unit Tests First** - Fast feedback during development, catch logic errors quickly
2. **Write Integration Tests Second** - Validate system behavior, concurrency, and database constraints

## Unit Testing

Pure unit tests validate business logic in isolation without database dependencies. They use an in-memory `EventStore` implementation that stores events directly (no JSON serialization) and uses real projection logic.

### When to Use Unit Tests

**Use unit tests for:**
- Happy path scenarios, business rule validation
- Balance calculations, state transitions
- Event generation logic, domain exception handling

**Use integration tests instead for:**
- DCB concurrency conflicts, database constraints
- Transaction boundaries, idempotency with real database

### Infrastructure Components

#### InMemoryEventStore

**Location:** `com.crablet.command.handlers.unit.InMemoryEventStore`

In-memory `EventStore` implementation:
- Stores original event objects directly (no JSON serialization)
- Uses real `StateProjector` logic for accurate projections
- Accepts all appends (no DCB concurrency checks for unit tests)
- Fast and lightweight - no database overhead

#### AbstractHandlerUnitTest

**Location:** `com.crablet.command.handlers.unit.AbstractHandlerUnitTest`

BDD-style base class providing:
- `given()` - Builder callback pattern for event seeding
- `when()` - Execute handler and extract pure domain events
- `whenWithTags()` - Execute handler and get events with tags
- `then()` - Assert on single event with type extraction
- `thenMultipleOrdered()` - Assert on multiple events with count and order using pattern matching
- `thenMultipleWithTagsOrdered()` - Assert on multiple events with tags, count and order
- `at()` - Helper method for indexed access to events in lists
- Pattern matching with sealed interfaces (no casts, no default clauses needed)

### Quick Start

Extend `AbstractHandlerUnitTest` and start writing tests:

```java
@DisplayName("DepositCommandHandler Unit Tests")
class DepositCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private DepositCommandHandler handler;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
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
            assertThat(deposit.newBalance()).isEqualTo(1500);
        });
    }
}
```

**Reference:** See `DepositCommandHandlerUnitTest` for complete examples.

### Unit Test Examples

#### Basic Example: Balance Calculation

```java
@Test
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

#### Error Case Example

```java
@Test
void givenWalletWithInsufficientBalance_whenWithdrawing_thenInsufficientFundsException() {
    // Given
    given().event(type(WalletOpened.class), builder -> builder
        .data(WalletOpened.of("wallet1", "Alice", 100))
        .tag(WALLET_ID, "wallet1")
    );
    
    // When & Then
    WithdrawCommand command = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Shopping");
    assertThatThrownBy(() -> when(handler, command))
        .isInstanceOf(InsufficientFundsException.class);
}
```

#### Multiple Events with Order Assertions

```java
@Test
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
- `at(index, list)` helper provides indexed access
- Pattern matching with sealed interfaces (type-safe, compiler-enforced)

**Reference:** See `DepositCommandHandlerUnitTest` for complete examples including tag assertions.

### Troubleshooting

**Common Issues:**
- **Period helper not found**: Create domain-specific factory (see `WalletPeriodHelperTestFactory` as example)
- **Tag assertions failing**: Use `whenWithTags()` instead of `when()` to get events with tags
- **Projection not working**: Ensure events are seeded with correct tags matching your query patterns

**Best Practices:**
- Keep unit tests focused on single scenarios
- Use descriptive test names following Given-When-Then pattern
- Assert on pure domain events, not framework wrappers

## Integration Testing

Integration tests validate DCB concurrency, database constraints, and real database behavior. They use Testcontainers to spin up a real PostgreSQL database.

### When to Use Integration Tests

**Use integration tests for:**
- DCB concurrency conflicts
- Database constraints and transactions
- Real database behavior, end-to-end flows
- Idempotency with real database

**Note:** Write unit tests first to validate domain logic quickly, then add integration tests for system-level concerns.

### Testcontainers Setup

#### Add Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
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
```

#### Base Test Class

Use the framework's base test class:

```java
import com.crablet.eventstore.integration.AbstractCrabletTest;

public class WalletIntegrationTest extends AbstractCrabletTest {
    
    @Autowired
    protected EventStore eventStore;
    
    // Your test methods here
}
```

`AbstractCrabletTest` provides:
- Shared PostgreSQL Testcontainers container
- Automatic Flyway migrations
- EventStore and JdbcTemplate autowired

### Integration Test Example

```java
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
            walletId, withdrawalId, new BigDecimal("50")
        );
        CommandResult result = handler.handle(eventStore, command);
        
        // Then: withdrawal succeeded
        assertTrue(result.success());
        
        // Verify event was stored
        Query query = QueryBuilder.create()
            .hasTag("wallet_id", walletId)
            .eventNames("WithdrawalMade")
            .build();
        
        List<StoredEvent> events = eventRepository.query(query, Cursor.zero());
        assertEquals(1, events.size());
    }
    
    @Test
    void testIdempotentWithdrawal() {
        String walletId = "wallet-" + System.currentTimeMillis();
        String withdrawalId = "withdrawal-duplicate";
        
        WithdrawCommand command = new WithdrawCommand(
            walletId, withdrawalId, new BigDecimal("50")
        );
        
        // When: withdraw twice with same ID
        CommandResult result1 = handler.handle(eventStore, command);
        CommandResult result2 = handler.handle(eventStore, command);
        
        // Then: both succeed (idempotent), but only one event stored
        assertTrue(result1.success());
        assertTrue(result2.success());
        
        Query query = QueryBuilder.create()
            .hasTag("wallet_id", walletId)
            .hasTag("withdrawal_id", withdrawalId)
            .build();
        
        List<StoredEvent> events = eventRepository.query(query, Cursor.zero());
        assertEquals(1, events.size(), "Should only store one withdrawal event");
    }
}
```

### Testing DCB Conflicts

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

## EventRepository

EventRepository provides direct access to raw events, bypassing the DCB pattern. It is optional and free for use anywhere in your application.

**Note:** For use cases requiring DCB concurrency control, use `EventStore.project()` instead.

```java
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

**When to Use EventRepository:**
- Verifying events were stored, checking event order
- Inspecting event tags, debugging
- Migration scripts, any use case where you need direct access to raw events

**Use EventStore.project() instead for:**
- Command handlers (requires DCB concurrency control)
- Business logic requiring state projection
- Use cases where you need concurrency control

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
        walletId, withdrawalId, new BigDecimal("-10")  // Negative amount
    );
    
    assertThrows(IllegalArgumentException.class, 
        () -> handler.handle(eventStore, command));
}
```

### 4. Test DCB Scenarios

Always test:
- Normal case (no conflicts)
- Concurrent modifications (conflicts)
- Duplicate operations (idempotency)
- Insufficient balance, invalid input

## Test Structure

The test scope is organized into:

- **Unit Tests** (`com.crablet.command.handlers.*.unit.*`): Pure unit tests for business logic
  - Fast, isolated, no database dependencies
  - Use `AbstractHandlerUnitTest` base class

- **Integration Tests** (`com.crablet.command.handlers.*.integration.*`): Integration tests with Testcontainers
  - Real database testing, DCB concurrency validation
  - Use `AbstractCrabletTest` base class

- **Framework Tests** (`com.crablet.eventstore.integration.*`): Integration tests for core EventStore functionality

- **Example Domains** (`com.crablet.examples.*`): Complete working examples demonstrating DCB patterns
  - `com.crablet.examples.wallet`: Wallet domain with deposits, withdrawals, transfers
  - `com.crablet.examples.course`: Course subscriptions with multi-entity constraints
  - Each includes domain events, commands, handlers, projectors, and tests

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
