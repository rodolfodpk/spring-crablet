# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference: Common Code Snippets

### Command Handler Template

Pick the sub-interface that matches the DCB pattern for your operation:

**Non-commutative** (streamPosition-based check — e.g. Withdraw, Transfer):
```java
@Component
public class YourCommandHandler implements NonCommutativeCommandHandler<YourCommand> {

    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, YourCommand command) {
        // 1. Project current state
        Query decisionModel = YourQueryPatterns.yourDecisionModel(command.entityId());
        ProjectionResult<YourState> projection = eventStore.project(decisionModel, yourProjector);
        YourState state = projection.state();

        // 2. Validate business rules
        if (!state.isValid()) {
            throw new YourBusinessException("Reason");
        }

        // 3. Generate event
        YourEvent event = YourEvent.of(/* ... */);
        AppendEvent appendEvent = AppendEvent.builder(type(YourEvent.class))
            .tag(YOUR_TAG_KEY, command.entityId())
            .data(event)
            .build();

        // 4. Return decision — CommandExecutor calls appendNonCommutative automatically
        return CommandDecision.NonCommutative.of(appendEvent, decisionModel, projection.streamPosition());
    }
}
```

**Commutative with lifecycle guard** (order-independent but requires entity to be active — e.g. Deposit, Credit):
```java
@Component
public class YourCommandHandler implements CommutativeCommandHandler<YourCommand> {

    @Override
    public CommandDecision.CommutativeDecision decide(EventStore eventStore, YourCommand command) {
        // 1. Project lifecycle state
        Query lifecycleModel = YourQueryPatterns.yourLifecycleModel(command.entityId());
        ProjectionResult<YourState> projection = eventStore.project(lifecycleModel, yourProjector);
        YourState state = projection.state();

        // 2. Validate business rules
        if (!state.isExisting()) {
            throw new YourEntityNotFoundException(command.entityId());
        }

        // 3. Generate event
        YourEvent event = YourEvent.of(/* ... */);
        AppendEvent appendEvent = AppendEvent.builder(type(YourEvent.class))
            .tag(YOUR_TAG_KEY, command.entityId())
            .data(event)
            .build();

        // 4. Guard query: lifecycle events only — NOT the commutative event type —
        //    so concurrent operations of the same type do not conflict with each other.
        Query lifecycleGuard = YourQueryPatterns.yourLifecycleGuard(command.entityId());
        return CommandDecision.CommutativeGuarded.withLifecycleGuard(appendEvent, lifecycleGuard, projection.streamPosition());
    }
}
```

**Commutative (pure, no guard — e.g. analytics events with no lifecycle dependency):**
```java
@Component
public class YourCommandHandler implements CommutativeCommandHandler<YourCommand> {

    @Override
    public CommandDecision.CommutativeDecision decide(EventStore eventStore, YourCommand command) {
        // generate event ...
        return CommandDecision.Commutative.of(appendEvent);
    }
}
```

**Idempotent** (entity creation — e.g. OpenWallet, DefineCourse):
```java
@Component
public class YourCommandHandler implements IdempotentCommandHandler<YourCommand> {

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, YourCommand command) {
        // generate event ...
        return CommandDecision.Idempotent.of(appendEvent, type(YourEvent.class), TAG_KEY, command.entityId());
    }
}
```

### View Projector Template

```java
@Component
public class YourViewProjector extends AbstractTypedViewProjector<YourEvent> {

    public YourViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    @Override
    public String getViewName() {
        return "your_view_name";
    }

    @Override
    protected Class<YourEvent> getEventType() {
        return YourEvent.class;
    }

    @Override
    protected boolean handleEvent(YourEvent event, StoredEvent stored, JdbcTemplate jdbc) {
        return switch (event) {
            case EntityCreated created -> {
                jdbc.update(
                    "INSERT INTO your_view_table (id, field) VALUES (?, ?) " +
                    "ON CONFLICT (id) DO UPDATE SET field = EXCLUDED.field",
                    created.id(), created.field()
                );
                yield true;
            }
            case EntityUpdated updated -> {
                jdbc.update("UPDATE your_view_table SET field = ? WHERE id = ?",
                    updated.field(), updated.id());
                yield true;
            }
            default -> false;
        };
    }
}
```

### Unit Test Template

```java
class YourHandlerUnitTest extends AbstractHandlerUnitTest {

    private YourCommandHandler handler;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new YourCommandHandler(/* ... */);
    }

    @Test
    void givenExistingEntity_whenExecutingCommand_thenEventGenerated() {
        // Given
        var created = EntityCreated.of("entity1", "data");
        given().event(type(EntityCreated.class), builder -> builder
            .data(created)
            .tag(ENTITY_ID, created.id())
        );

        // When
        YourCommand command = YourCommand.of("entity1", "param");
        List<Object> events = when(handler, command);

        // Then
        then(events, YourEvent.class, event -> {
            assertThat(event.entityId()).isEqualTo("entity1");
            assertThat(event.param()).isEqualTo("param");
        });
    }
}
```

## Build Commands

See [BUILD.md](docs/BUILD.md) for full details. Quick reference:

```bash
make install            # Full build with unit tests (recommended)
make install-all-tests  # Full build including integration tests
make test               # Run all tests
make clean              # Clean build artifacts
make start              # Run wallet-example-app

# Run specific module tests (after make install)
./mvnw test -pl <module-name>
./mvnw test -pl <module-name> -Dtest=ClassName
```

**Testing:**
- Tests use Testcontainers (Docker required, no external PostgreSQL needed)
- Unit tests: Fast in-memory tests (< 10ms)
- Integration tests: Real PostgreSQL via Testcontainers (100-500ms)

## Project Architecture

### Overview

Spring-Crablet is a lightweight Java 25 event sourcing framework built on the DCB (Dynamic Consistency Boundary) pattern. It provides event sourcing with optimistic concurrency control using streamPosition-based checks instead of distributed locks.

**Key Technologies:**
- Java 25 (records, sealed interfaces, virtual threads)
- Spring Boot 4.0
- PostgreSQL 17+ (MVCC, advisory locks, GIN indexes)
- Maven multi-module project

### Module Structure

```
crablet-eventstore (CORE - Required)
├── EventStore interface - Core event sourcing API
├── DCB implementation - Optimistic concurrency control
├── Query/StreamPosition/Tag - Event filtering and position tracking
└── StateProjector - State reconstruction from events

crablet-commands (Optional)
├── CommandHandler interface - Command handling pattern
├── CommandExecutor - Automatic handler discovery and orchestration
└── CommandDecision - Decision about which DCB pattern to apply

crablet-event-poller (Generic Infrastructure)
├── EventProcessor - Reusable polling infrastructure
├── Leader election - PostgreSQL advisory locks
├── Progress tracking - Per-processor position tracking
└── Used by crablet-views and crablet-outbox

crablet-views (Optional)
├── ViewProjector interface - Materialized read models
├── Async event processing - Eventual consistency
└── Leader election per view

crablet-outbox (Optional)
├── OutboxPublisher interface - External event publishing
├── Transactional outbox pattern
└── Multiple publishers support

crablet-automations (Optional)
├── Event-driven automations - Policies and sagas
├── Listen to events, execute commands automatically
└── Leader election per automation processor

crablet-metrics-micrometer (Optional)
└── Auto-collects metrics from all modules

shared-examples-domain (Non-reactor, separate build)
├── Wallet domain - Complete working example
├── Course domain - Multi-entity constraints example
└── Used by all modules in test scope

wallet-example-app (Example application)
└── Complete Spring Boot application demonstrating framework usage
```

**Module dependencies:**
- `crablet-eventstore`: No dependencies on other modules
- `crablet-commands`: Depends on `crablet-eventstore`
- `crablet-event-poller`: Depends on `crablet-eventstore`
- `crablet-views`: Depends on `crablet-eventstore` + `crablet-event-poller`
- `crablet-outbox`: Depends on `crablet-eventstore` + `crablet-event-poller`
- `crablet-automations`: Depends on `crablet-eventstore` + `crablet-event-poller` + `crablet-commands`

### Cyclic Dependency Handling

**Important:** There is an intentional cyclic dependency between `crablet-eventstore` and `shared-examples-domain`:
- `shared-examples-domain` depends on `crablet-eventstore` (main scope) - uses framework interfaces
- `crablet-eventstore` depends on `shared-examples-domain` (test scope) - uses examples in tests

**Rationale:** This design allows all framework modules to share the same example domains in their tests, ensuring:
1. Consistency across module tests (all test against same domain logic)
2. Realistic testing (real domain implementations, not mocks)
3. Living documentation (examples show actual framework usage)
4. Test infrastructure reuse (`AbstractHandlerUnitTest`, `InMemoryEventStore`)

**Trade-off:** Build complexity (requires specific build order) vs maintainability and test quality. The Makefile handles this automatically via stub JARs and ordered builds.

**Both `shared-examples-domain` and `wallet-example-app` are excluded from the reactor** to avoid cyclic dependency issues.

**When creating your own application:**
- Your application depends on framework modules (normal dependency)
- Your application does NOT need to be in the same build (separate repository is fine)
- Framework tests use `shared-examples-domain`, your tests use your own domain

### Refactoring: Eliminating Cyclic Dependencies (COMPLETED ✅)

**Goal:** Eliminate cyclic dependencies between framework modules and `shared-examples-domain` by introducing `crablet-test-support` module.

**Current Problem:**
```
crablet-eventstore (test scope) → shared-examples-domain (main scope) → crablet-eventstore (main scope)
crablet-commands (test scope) → shared-examples-domain (main scope) → crablet-commands (main scope)
```

**Solution: Create `crablet-test-support` Module**

**Architecture:**
```
crablet-test-support (NEW MODULE)
├── Dependencies: crablet-eventstore (main scope only)
├── Contains: Test utilities (InMemoryEventStore, AbstractCrabletTest, DCBTestHelpers)
├── Build order: After crablet-eventstore, before reactor
└── Used by: All modules in test scope

Flow:
1. crablet-eventstore (main) → NO dependencies on examples
2. crablet-test-support → depends on crablet-eventstore (main)
3. shared-examples-domain → depends on crablet-eventstore + crablet-test-support (test)
4. All other modules → depend on crablet-test-support (test scope)
```

**Implementation Steps:**

1. **Create `crablet-test-support` module**
   - Location: `crablet-test-support/`
   - Dependencies: `crablet-eventstore` (main scope), Spring Test, Testcontainers, PostgreSQL, Jackson
   - Scope: Provides test utilities to all modules

2. **Move test utilities to `crablet-test-support`**
   - `InMemoryEventStore` → from `crablet-commands/src/test/java` to `crablet-test-support/src/main/java/com/crablet/test/`
   - `AbstractCrabletTest` → from `crablet-eventstore/src/test/java` to `crablet-test-support/src/main/java/com/crablet/test/`
   - `DCBTestHelpers` → from `crablet-eventstore/src/test/java` to `crablet-test-support/src/main/java/com/crablet/eventstore/integration/`

3. **Handle wallet-dependent integration tests**
   - Tests remain in `crablet-eventstore`: `EventStoreTest`, `EventStoreErrorHandlingTest`, `EventStoreQueryTest`, `ClosingBooksPatternTest`
   - `crablet-eventstore` keeps `shared-examples-domain` as test scope dependency for these tests
   - This allows framework tests to verify functionality with real domain examples

4. **Update module dependencies**
   - `crablet-eventstore`: Add `crablet-test-support` (test scope), keep `shared-examples-domain` (test scope) for wallet-dependent tests
   - `crablet-commands`: Replace eventstore test-jar with `crablet-test-support` (test scope)
   - `shared-examples-domain`: Add `crablet-test-support` (test scope)
   - All other modules: Add `crablet-test-support` (test scope) as needed

5. **Update imports and extends clauses**
   - Change `import com.crablet.eventstore.integration.AbstractCrabletTest` to `import com.crablet.test.AbstractCrabletTest`
   - Change `extends com.crablet.eventstore.integration.AbstractCrabletTest` to `extends com.crablet.test.AbstractCrabletTest`
   - Update all DCBTestHelpers imports

6. **Update build order**
   - Makefile: Build `crablet-test-support` after `crablet-eventstore` (main), before reactor
   - Reactor pom: Exclude `crablet-test-support` and `shared-examples-domain` from reactor
   - Create stub JAR for `crablet-test-support` in Makefile

7. **Copy database migrations**
   - Copy migrations to `crablet-commands/src/test/resources/db/migration/`
   - Required because integration tests need access to schema (V1__eventstore_schema.sql, V2__outbox_schema.sql, V3__view_progress_schema.sql)

8. **Verify all tests pass** ✅
   - ✅ Ran full build: `make install`
   - ✅ All 900+ tests pass
   - ✅ No cyclic dependency errors from Maven
   - ✅ Committed to `feature/eliminate-cyclic-dependency-v2` (commit 95ccc23)

**Benefits:**
- Clean dependency graph (no cycles)
- Test utilities available to all modules
- Simplified build process (no stub JAR workarounds needed)
- Better separation of concerns (test code in dedicated module)
- Framework tests remain independent of example domains

**Build Order After Refactoring:**
```
1. crablet-eventstore (main only, skip tests)
2. crablet-test-support (full build with tests)
3. shared-examples-domain (full build with tests)
4. Reactor modules (all modules with full tests)
```

**Status:** ✅ **COMPLETED** - All 900+ tests passing, cyclic dependencies eliminated, committed to `feature/eliminate-cyclic-dependency-v2` branch (commit 95ccc23)

### DCB (Dynamic Consistency Boundary) Pattern

DCB is the core architectural pattern that replaces traditional aggregate-based event sourcing.

**Official DCB Specification**: https://dcb.events/

**Note**: Spring-Crablet implements the core DCB principles but doesn't strictly follow the official spec. Our implementation uses streamPosition-based optimistic locking with tag-based queries, which aligns with DCB's philosophy of "context-sensitive consistency enforcement without rigid transactional boundaries."

**Core DCB Principle** (from spec):
- Technique for enforcing consistency in event-driven systems without rigid transactional boundaries
- Balances strong consistency with scalability
- Event tagging allows a single event to impact multiple entities within a bounded context
- Query-based optimistic locking (not stream-based revisions)
- Enables parallel, unrelated writes while maintaining consistency for cross-entity constraints

**Crablet's three append methods** (library API — not DCB spec vocabulary):

1. **`appendNonCommutative`** (non-commutative operations):
   - Use for: Operations on existing entities (Withdraw, Transfer)
   - Detects concurrent modifications via stream position

2. **`appendIdempotent`** (entity creation):
   - Use for: Preventing duplicate entity creation (OpenWallet)
   - Fails if event with same tag already exists

3. **`appendCommutative`** (order-independent operations):
   - Use for: Order-independent operations (Deposit, Credit)
   - `Commutative` — no conflict detection (truly state-independent operations)
   - `CommutativeGuarded` — concurrent same-type operations are still allowed, but atomically checks that no lifecycle event (e.g., `EntityClosed`) appeared after the projected position; use this when the entity must be active to accept the operation

**DCB Flow:**
1. Project current state using `eventStore.project(query, streamPosition, stateType, projectors)`
2. Returns `ProjectionResult<T>` with both state and stream position
3. Validate business rules against projected state
4. Generate events
5. Call the appropriate append method — atomic check and append

**Key files:**
- DCB explanation: `crablet-eventstore/docs/DCB_AND_CRABLET.md`
- Command patterns: `crablet-eventstore/docs/COMMAND_PATTERNS.md`

### Core Abstractions

**EventStore (crablet-eventstore/src/main/java/com/crablet/eventstore/EventStore.java):**
- `appendCommutative(events)` - Append without commutative-operation conflict (deposits, credits); used by both `Commutative` and `CommutativeGuarded` decisions — the guard check is performed before the append by `CommandExecutorImpl`
- `appendNonCommutative(events, decisionModel, streamPosition)` - Append with DCB stream-position check (withdrawals, transfers)
- `appendIdempotent(events, eventType, tagKey, tagValue)` - Append with duplicate-entity guard (entity creation)
- `project(query, streamPosition, stateType, projectors)` - State reconstruction
- `executeInTransaction(operation)` - Transaction wrapper

**CommandAuditStore (crablet-eventstore/src/main/java/com/crablet/eventstore/CommandAuditStore.java):**
- `storeCommand(json, type, txId)` - Command audit trail (implemented by `EventStoreImpl`; accessed via `instanceof` cast in `CommandExecutorImpl`)

**CommandExecutors (crablet-commands/src/main/java/com/crablet/command/CommandExecutors.java):**
- Public factory for creating `CommandExecutor`
- Preferred wiring entry point instead of instantiating `CommandExecutorImpl` directly

**AppendEvent vs StoredEvent:**
- `AppendEvent` - For writing (type, tags, data)
- `StoredEvent` - For reading (includes position, occurredAt, transactionId)

**Query (crablet-eventstore/src/main/java/com/crablet/eventstore/query/):**
- Defines which events are relevant for decisions
- Used for filtering, DCB conflict detection, state projection
- Built with `QueryBuilder`

**StreamPosition:**
- Captures event position (sequence number + timestamp + txId)
- Returned from `project()` via `ProjectionResult.streamPosition()`
- Core of DCB optimistic concurrency control

**StateProjector (crablet-eventstore/src/main/java/com/crablet/eventstore/projector/):**
- `transition(currentState, event, deserializer)` - Event-driven state transitions
- `getEventTypes()` - Filter which events to process
- `getInitialState()` - Starting state
- `StateProjector.exists(eventTypes...)` - Built-in factory for existence checks (returns `true` on first matching event)
- `EventStore.exists(query)` - Convenience shorthand: `boolean exists = eventStore.exists(query);`

**CommandHandler (crablet-commands/src/main/java/com/crablet/command/):**
- `handle(eventStore, command)` - Returns `CommandDecision`
- CommandExecutor automatically calls the correct append method based on the `CommandDecision` type
- All wrapped in single database transaction

### Testing Patterns

**Unit Testing (Fast, < 10ms):**
- Base class: `AbstractHandlerUnitTest` (in `crablet-test-support`)
- In-memory: `InMemoryEventStore` - no JSON serialization
- BDD-style: `given()`, `when()`, `then()`, `thenMultipleOrdered()`
- Pattern matching with sealed interfaces

**Integration Testing (Slower, 100-500ms):**
- Base class: `AbstractCrabletTest` (in `crablet-test-support`)
- Real PostgreSQL via Testcontainers
- Tests DCB concurrency, database constraints, idempotency

**Test utilities location:**
- `crablet-test-support/src/main/java/com/crablet/test/` - InMemoryEventStore, AbstractCrabletTest
- `crablet-test-support/src/main/java/com/crablet/eventstore/integration/` - DCBTestHelpers
- Maven dependency: `com.crablet:crablet-test-support` (test scope)

### Period Segmentation (@PeriodConfig)

Closing the books pattern for performance optimization with large event histories.

**Annotation:**
```java
@PeriodConfig(PeriodType.MONTHLY)
public interface WalletCommand {
    String getWalletId();
}
```

**Period types:** `MONTHLY`, `DAILY`, `HOURLY`, `YEARLY`, `NONE` (default)

**Framework provides:** `@PeriodConfig` annotation and `PeriodType` enum
**You implement:** Period helpers, resolvers, statement events, period-aware queries

**Documentation:** `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`

### View Projections (crablet-views)

Asynchronous materialized read models from events.

**Architecture:**
- Polling (1s interval) → Filter by tags → View projector → Database view
- Leader election: Only 1 instance processes per view
- Idempotent: Events processed at-least-once
- Progress tracked per view

**Base classes:**
- `AbstractTypedViewProjector<E>` - Recommended (sealed interfaces, pattern matching)
- `AbstractViewProjector` - Simple events without sealed interfaces
- Direct `ViewProjector` - Maximum flexibility

**Key principles:**
- Use `ON CONFLICT` or upserts for idempotency
- Each batch committed atomically
- Tag-based filtering for event subscriptions
- View projection runs on the write-side datasource via the poller/handler boundary

### Event Publishing (crablet-outbox)

Transactional outbox for reliable external event publishing.

**Architecture:**
- Events → Outbox table (same tx) → Polling → Multiple publishers → External systems
- Leader election with global lock strategy
- Per-publisher schedulers (independent polling)
- The generic poller owns fetch, progress, and retry/error tracking; outbox publishers only publish the batch they receive

**OutboxPublisher interface:**
- `publish(topic, events)` - Publish to external system
- `getName()` - Publisher identifier

### Leader Election

PostgreSQL advisory locks for distributed coordination.

**Mechanism:**
- `pg_try_advisory_lock(hashcode)` - Non-blocking lock attempt
- Leader: Acquired lock, processes events
- Follower: Failed lock, waits for next cycle
- Automatic failover: 5-30 seconds
- Advisory locks are session-scoped; leader election must stay on a session-safe write connection
- With PgBouncer or PgCat, keep leader election on the primary/write path in session mode

**Documentation:** `docs/LEADER_ELECTION.md`

### DataSource Model

Crablet intentionally exposes two datasource roles:

- `primaryDataSource` for writes, progress tracking, and leader election
- `readDataSource` for read-only fetches that may be served by replicas

Prefer explicit read/write endpoints over relying on pooler SQL parsing for correctness.

## Common Gotchas & Troubleshooting

### Build Issues

**Problem:** "Cannot find symbol: shared-examples-domain classes"
- **Solution:** Run `make install` or manually build in order: `crablet-eventstore` → `shared-examples-domain` → reactor modules

**Problem:** Tests fail with "Docker not running"
- **Solution:** Integration tests use Testcontainers. Either start Docker or run unit tests only: `./mvnw test -DexcludedGroups=integration`

### DCB Pattern Issues

**Problem:** `ConcurrencyException` thrown when no concurrent modifications occurred
- **Enhanced Diagnostics (v1.0+):** The exception now includes diagnostic hints and matching event counts
- **Solution:** Check that your decision model query matches the tags on your events. Mismatched tags cause false conflicts.
- **Debug:** Enable debug logging (`logging.level.com.crablet.eventstore=DEBUG`) to see DCB check details

**Problem:** Idempotency check not working (duplicate events stored)
- **Enhanced Diagnostics (v1.0+):** Exception message now includes specific hint about idempotency tag usage
- **Solution:** Ensure you're using `AppendCondition.idempotent()` with the correct event type and tag. The tag must uniquely identify the operation (e.g., `deposit_id`, not just `wallet_id`).
- **Debug:** Check exception message for `Hint:` section with guidance

**Problem:** Query returns no events but events exist in database
- **Solution:** Verify tags on events match query criteria. Common issue: forgetting period tags (`year`, `month`) when using closing the books pattern.
- **Debug:** Enable debug logging to see exact query parameters being sent to PostgreSQL

### Testing Issues

**Problem:** Unit test projections return wrong state
- **Solution:** Ensure you seed events with `.tag()` calls matching your query patterns. `InMemoryEventStore` filters by tags just like the real implementation.

**Problem:** `ClassCastException` in tests
- **Solution:** Use pattern matching with sealed interfaces instead of casts. If event isn't sealed, extend `AbstractViewProjector` instead of `AbstractTypedViewProjector`.

**Problem:** Integration test database pollution between tests
- **Solution:** Use unique IDs: `String walletId = "wallet-" + System.currentTimeMillis();` or `UUID.randomUUID().toString()`

### View Projection Issues

**Problem:** View not updating (events not processed)
- **Solution:** Check leader election - only one instance processes. Verify with logs: "Leader election acquired for view: {viewName}"

**Problem:** View falls behind (lag increasing)
- **Solution:** Check batch size, processing time, and error logs. Consider increasing batch size or optimizing projector logic.

**Problem:** View projector throws exceptions but silently fails
- **Enhanced Diagnostics (v1.0+):** Error logs now include full event context (type, position, transaction_id, tags)
- **Solution:** Views swallow exceptions by design (at-least-once processing). Check logs for enhanced error details including the exact event that failed.
- **Debug:** Error message now includes hint to check if projector handles all event types

### Event Deserialization Issues

**Problem:** `JsonMappingException` when deserializing events
- **Solution:** Ensure event classes are properly configured for Jackson (records work out of the box). Check that field names match JSON.

**Problem:** Events stored but projector can't read them
- **Solution:** Verify `getEventTypes()` in projector returns correct event type names. Use `EventType.type(Class)` pattern for consistency.

## Common Development Tasks

### Adding a New Command

1. Define command interface/record in domain package
2. Implement `CommandHandler<YourCommand>`
3. Annotate handler with `@Component`
4. Define events and decision model query
5. Implement state projector if needed
6. Write unit tests extending `AbstractHandlerUnitTest`
7. Write integration tests extending `AbstractCrabletTest`

### Adding a New View

1. Create view table schema (Flyway migration)
2. Implement `ViewProjector` (extend `AbstractTypedViewProjector`)
3. Annotate with `@Component`
4. Implement `handleEvent()` with idempotent upserts
5. Configure view filters (event types, tags)
6. Test with integration tests

### Running Wallet Example App

```bash
make start
# or
cd wallet-example-app && ./mvnw spring-boot:run
```

**Endpoints:**
- API: http://localhost:8080/api/
- Swagger: http://localhost:8080/swagger-ui.html
- View Management: http://localhost:8080/api/views/{viewName}/status

## Important Coding Patterns & Conventions

### Naming Conventions

**Tags** (snake_case):
- Use constants from domain-specific tag classes (e.g., `WalletTags.WALLET_ID`, `WalletTags.DEPOSIT_ID`)
- Format: `wallet_id`, `deposit_id`, `withdrawal_id`, `transfer_id`, `from_wallet_id`, `to_wallet_id`
- Period tags: `year`, `month`, `day`, `hour` (for closing the books pattern)
- **Tag keys are automatically normalized to lowercase** at construction time (`Tag` compact constructor, `Locale.ROOT`). Passing `"WALLET_ID"` is equivalent to `"wallet_id"`. Tag **values** are case-sensitive and never modified.

**Event Types** (PascalCase):
- Use `EventType.type(EventClass.class)` pattern for type-safe event type names
- Examples: `type(WalletOpened.class)`, `type(DepositMade.class)`, `type(WithdrawalMade.class)`

**Event Classes**:
- Use sealed interfaces for pattern matching: `sealed interface WalletEvent permits WalletOpened, DepositMade, ...`
- Records for event data: `record DepositMade(String depositId, String walletId, int amount, ...) implements WalletEvent`

### Command Validation Pattern (YAVI)

Commands validate themselves at construction using YAVI:

```java
public record OpenWalletCommand(String walletId, String owner, int initialBalance) {
    private static Arguments3Validator<String, String, Integer, OpenWalletCommand> validator =
        Yavi.arguments()
            ._string("walletId", c -> c.notNull().notBlank())
            ._string("owner", c -> c.notNull().notBlank())
            ._integer("initialBalance", c -> c.greaterThanOrEqual(0))
            .apply(OpenWalletCommand::new);

    public OpenWalletCommand {
        try {
            validator.lazy().validated(walletId, owner, initialBalance);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid command: " + e.getMessage(), e);
        }
    }
}
```

**Pattern:** Validation happens at construction, not in handlers. Handlers can assume commands are valid.

### Query Building Patterns

**Pattern 1: Events with shared tag**
```java
QueryBuilder.builder()
    .events(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class))
    .tag(WALLET_ID, walletId)
    .build();
```

**Pattern 2: Single event type with specific tag**
```java
QueryBuilder.builder()
    .event(type(MoneyTransferred.class), FROM_WALLET_ID, walletId)
    .build();
```

**Pattern 3: Events with multiple tags (all must match)**
```java
QueryBuilder.builder()
    .matching(
        new String[]{type(WalletStatementOpened.class)},
        QueryBuilder.tag(WALLET_ID, walletId),
        QueryBuilder.tag(YEAR, String.valueOf(year)),
        QueryBuilder.tag(MONTH, String.valueOf(month))
    )
    .build();
```

**Reusable Query Patterns**: Create domain-specific query pattern classes (e.g., `WalletQueryPatterns.singleWalletDecisionModel(walletId)`)

### Domain Exception Pattern

Handlers throw domain-specific exceptions for business rule violations:

```java
if (!state.hasSufficientFunds(amount)) {
    throw new InsufficientFundsException(walletId, state.balance(), amount);
}
if (!state.isExisting()) {
    throw new WalletNotFoundException(walletId);
}
```

**Common exception types:**
- Entity not found: `WalletNotFoundException`, `CourseNotFoundException`
- Business rule violations: `InsufficientFundsException`, `CourseFullException`
- Duplicate operations: `DuplicateOperationException`, `AlreadySubscribedException`

### Database Schema Key Points

**events table:**
- `position` (BIGSERIAL) - Global sequence number, primary key
- `transaction_id` (xid8) - PostgreSQL transaction ID for ordering
- `tags` (TEXT[]) - Array of tags with GIN index for fast filtering
- `data` (JSON) - Event payload
- `type` (VARCHAR 64) - Event type name
- `occurred_at` (TIMESTAMP WITH TIME ZONE) - Event timestamp

**Key indexes:**
- `idx_events_tags` (GIN) - Fast tag-based filtering
- `idx_events_type_position` - Optimized for DCB query pattern
- `idx_events_type_tags_gin` - Optimized for idempotency checks

**PostgreSQL functions:**
- `append_events_if()` - Atomic DCB check and append (uses advisory locks for idempotency)
- `append_events_batch()` - Batch insert using UNNEST

**commands table:**
- Stores command audit trail with transaction_id linking to events

### Spring Boot Auto-Configuration

Modules use Spring Boot auto-configuration via `META-INF/spring.factories`:
- `crablet-views`: Auto-configures view processing infrastructure
- `crablet-outbox`: Auto-configures outbox processing infrastructure
- Add `@SpringBootApplication` and the framework auto-wires itself

## Important Architectural Principles

1. **Idempotency**: All operations must be idempotent (views, outbox, command handlers with idempotency checks)

2. **Atomicity**: Command handler + events + audit = single transaction

3. **StreamPosition-based Concurrency**: Capture stream position from projection, use in AppendCondition for DCB checks

4. **Event Immutability**: Events are immutable once appended

5. **At-Least-Once Processing**: Views and outbox process events at-least-once (progress updates in separate transaction)

6. **Leader Election**: Only one instance processes per view/outbox scheduler

7. **Read/Write Separation**: Commands via EventStore (write model), queries via views (read model, eventual consistency)

8. **Tag-based Filtering**: Tags are the primary mechanism for event filtering and decision models

9. **Type Safety**: Use `EventType.type(Class)` for event types and sealed interfaces for pattern matching

## Developer Experience Improvements

### Enhanced Error Diagnostics (v1.0+)

The framework includes enhanced error diagnostics to help troubleshoot common issues:

**1. ConcurrencyException Improvements:**
- Includes DCBViolation details with error code and matching event count
- Automatic diagnostic hints based on violation type
- `getDiagnostics()` method for structured logging
- Clear guidance on idempotency vs concurrency violations

Example enhanced message:
```
AppendCondition violated: duplicate operation detected | DCBViolation{errorCode='IDEMPOTENCY_VIOLATION', message='duplicate operation detected', matchingEvents=1}
  Hint: Check that your idempotency tag uniquely identifies the operation (e.g., deposit_id, not just wallet_id)
```

**2. Debug Logging for DCB Checks:**
- Enable with: `logging.level.com.crablet.eventstore=DEBUG`
- Shows exact query parameters, stream position, tags being checked
- Helps diagnose query/tag mismatches
- Useful for understanding why concurrency exceptions occur

Example debug output:
```
AppendIf DCB checks: events=[DepositMade], streamPosition=42, concurrencyTypes=[WalletOpened, DepositMade],
concurrencyTags=[wallet_id=alice], idempotencyTypes=[DepositMade], idempotencyTags=[deposit_id=d123]
```

**3. Enhanced View Projector Errors:**
- Full event context in error logs (type, position, transaction_id, tags)
- Hints about missing event type handlers
- Clear exception messages with view name and event details
- Easier to identify which specific event caused projection failure

Example enhanced error:
```
Failed to project event for view 'wallet_balance'. Event details: type=DepositMade, position=12345,
transaction_id=1234567, occurred_at=2024-01-15T10:30:00Z, tags=[wallet_id=alice, deposit_id=d123].
Error: Column 'amount' not found. Hint: Check that your view projector handles all event types in getEventTypes().
```

### Troubleshooting Workflow

**When you encounter a ConcurrencyException:**
1. Read the hint in the exception message (idempotency vs concurrency)
2. Check the `matchingEventsCount` - shows how many conflicting events exist
3. Enable DEBUG logging to see exact DCB check parameters
4. Verify your query tags match the tags on your events

**When a view projector fails:**
1. Check the enhanced error log for full event details
2. Verify the event type is in your projector's `getEventTypes()`
3. Check if your SQL expects columns that don't exist in the event
4. Test with the specific event that failed (position and transaction_id are in logs)

## Performance Considerations

### Read Replicas (Optional)

Configure separate read and write datasources for horizontal scaling:

```java
@Bean
public EventStore eventStore(
    @Qualifier("primaryDataSource") DataSource writeDataSource,
    @Qualifier("readDataSource") DataSource readDataSource,
    // ...
) {
    return new EventStoreImpl(writeDataSource, readDataSource, ...);
}
```

**Benefits:**
- Event fetching (views, outbox) uses read replicas
- Command appends go to primary
- Reduces load on primary database
- Leader election and other session-scoped features must remain on `primaryDataSource`

### Automation Pattern

Recommended separation of concerns:

- command handlers record facts
- views model current decision state
- automations react asynchronously and own external side effects

If an automation needs to call an email provider, webhook, or other external HTTP API, keep that call in the automation layer and use commands/events to record the outcome.

**Documentation:** `crablet-eventstore/docs/READ_REPLICAS.md`

### Database Indexes

The framework relies heavily on PostgreSQL indexes for performance:

- **GIN index on tags** - Fast tag-based filtering (e.g., `WHERE tags @> '{wallet_id:alice}'`)
- **Composite index (type, position)** - Optimized for DCB query pattern
- **Composite GIN index (type, tags)** - Optimized for idempotency checks

**Key insight:** Tag-based filtering is O(log n) with GIN indexes, making it efficient even with millions of events.

### Batch Processing

**Views and Outbox:**
- Default batch size: 100 events
- Configurable via application properties
- Larger batches = better throughput, higher latency
- Smaller batches = lower latency, more database round-trips

**Event Appending:**
- Uses `UNNEST` for batch inserts (single database round-trip)
- `append_events_if()` function handles multiple events atomically

### Connection Pooling

Consider PgBouncer for connection pooling in production:
- Reduces connection overhead
- Handles connection limits gracefully
- Transaction-level pooling recommended

**Documentation:** `crablet-eventstore/docs/PGBOUNCER.md`

### Closing the Books Pattern

For entities with long event histories (millions of events):
- Use `@PeriodConfig` to segment events by period (monthly, daily, etc.)
- Query only current period events instead of full history
- Significant performance improvement for mature entities

**Documentation:** `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`

## Documentation Quick Links

- Build instructions: `docs/BUILD.md`
- EventStore README: `crablet-eventstore/README.md`
- Command framework: `crablet-commands/README.md`
- Event processor: `crablet-event-poller/README.md`
- Outbox: `crablet-outbox/README.md`
- Views: `crablet-views/README.md`
- Testing guide: `crablet-eventstore/TESTING.md`
- DCB explained: `crablet-eventstore/docs/DCB_AND_CRABLET.md`
- Command patterns: `crablet-eventstore/docs/COMMAND_PATTERNS.md`
- Closing books: `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`
- Leader election: `docs/LEADER_ELECTION.md`
- Read replicas: `crablet-eventstore/docs/READ_REPLICAS.md`
- Metrics: `crablet-eventstore/docs/METRICS.md`, `crablet-outbox/docs/OUTBOX_METRICS.md`

## Key Package Locations

**Core packages:**
- EventStore: `crablet-eventstore/src/main/java/com/crablet/eventstore/`
- Command framework: `crablet-commands/src/main/java/com/crablet/command/`
- Views: `crablet-views/src/main/java/com/crablet/views/`
- Outbox: `crablet-outbox/src/main/java/com/crablet/outbox/`

**Example domains:**
- Wallet: `shared-examples-domain/src/main/java/com/crablet/examples/wallet/`
- Course: `shared-examples-domain/src/main/java/com/crablet/examples/course/`

**Test utilities:**
- `crablet-test-support/src/main/java/com/crablet/test/` - InMemoryEventStore, AbstractCrabletTest
- `crablet-test-support/src/main/java/com/crablet/eventstore/integration/` - DCBTestHelpers
- Maven: `com.crablet:crablet-test-support` (test scope)
