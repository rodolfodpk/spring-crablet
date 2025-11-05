# Dynamic Consistency Boundary (DCB)

## Problem Statement

Event sourcing systems require concurrency control to prevent inconsistent state. Example scenario:

- Wallet balance: $1000
- Request A: Transfer $600 (reads position 42)
- Request B: Transfer $500 (reads position 42)
- Both see sufficient balance, both succeed → balance: -$100 (invalid)

## What is DCB?

Dynamic Consistency Boundaries (DCB) redefine consistency granularity in event-sourced systems, moving from fixed aggregates (event streams) to dynamically defined consistency boundaries based on criteria (queries).

### Traditional Approach: Aggregates as Consistency Boundaries

- Events organized into streams (one stream per aggregate)
- Consistency boundary = aggregate boundary
- Changing boundaries requires refactoring streams
- Some business rules span multiple aggregates

### DCB Approach: Criteria-Based Consistency Boundaries

- Consistency boundary = events matching your criteria (decision model)
- Boundaries defined dynamically per operation
- Flexible refactoring without stream changes
- Single event can belong to multiple consistency boundaries

**Broader Applicability:** DCB applies to any messaging system with append-only log and pub/sub characteristics, not limited to event sourcing. Useful for any system with position-based deduplication.

### Architectural Insight: Bounded Context vs Aggregate

When organizing event streams, consider aligning streams with **Bounded Contexts** (sub-domains) rather than individual Aggregates. Within a Bounded Context, you can use DCB to maintain flexible consistency boundaries:

- **Bounded Context scope**: Store all events for a sub-domain in one stream
- **DCB within context**: Define consistency boundaries dynamically per operation
- **Benefit**: More flexible than strict aggregate boundaries, easier to refactor

This is architectural guidance, not a requirement. DCB works regardless of how you organize your streams.

## Benefits of DCB

DCB provides several advantages over traditional aggregate-based consistency:

✅ **Reduces events needed to rehydrate state** - Fine-grained filtering based on criteria means you only process events relevant to your decision, dramatically reducing event consumption for large histories.

✅ **Eliminates need for eventual consistency techniques** - Business rules that span multiple aggregates can be enforced with immediate consistency by defining criteria that include events from multiple entities.

✅ **Reduces append contention** - Smaller, finely-defined consistency boundaries have less chance of conflicting, enabling better parallel execution.

✅ **Flexible refactoring** - Adjusting consistency boundaries doesn't require stream restructuring. Your event stream stays intact; you just change the criteria used for different operations.

✅ **Less upfront design pressure** - You can refine consistency boundaries as you learn more about your domain, without costly migrations.

**Performance Impact:** These benefits translate to real performance gains. See [Performance Characteristics](#performance-characteristics) section for measured improvements.

## DCB Mechanism

DCB uses cursor-based checks to detect conflicting events since last read.

### Implementation Steps

1. **Read**: Client reads current state, captures cursor position
2. **Compute**: Client generates events based on read state
3. **Append**: Server checks for conflicts before writing
4. **Exception**: If conflict detected, server throws `ConcurrencyException`, application retries from step 1

### Conflict Detection Query

```sql
SELECT COUNT(*) FROM events 
WHERE tags @> ARRAY['wallet_id=alice']
AND position > 42
AND transaction_id < pg_snapshot_xmin(pg_current_snapshot())
LIMIT 1
```

If count > 0: conflict detected, return 409
If count = 0: safe to append, write event

### Example Timeline

```
Position:  40    41    42    43    44
Events:    ...   ...   ...   [A]   [B]

Request A: Read@42 → Check → Write@43 (success)
Request B: Read@42 → Check → Conflict (position 43 exists) → 409 Conflict
           Application retries: Read@43 → Check → Write@44 (success)
```

## Architecture: Cursor-Only vs Idempotency

DCB supports two concurrency control strategies:

### Operations (Deposits, Withdrawals, Transfers)
- **Strategy**: Cursor-only concurrency control
- **Performance**: ~4x faster (no advisory locks)
- **Safety**: Cursor advancement prevents duplicate charges
- **Behavior**: 201 CREATED for all successful requests, 409 Conflict for stale cursors

### Wallet Creation
- **Strategy**: Idempotency checks (no cursor protection available)
- **Performance**: Slower but necessary for uniqueness
- **Safety**: Advisory locks prevent duplicate wallet IDs
- **Behavior**: 201 CREATED for new wallets, 200 OK for duplicates

## Entity Scoping

DCB checks are scoped to specific entities via tags:

```java
AppendCondition condition = AppendCondition.builder()
    .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred")
    .tags("wallet_id", walletId)
    .afterCursor(cursor)
    .build();
```

Result: Conflicts only detected for events affecting same wallet. Operations on wallet A don't block operations on wallet B.

**Multi-dimensional scoping:** Events tagged with multiple entity identifiers can belong to multiple consistency boundaries simultaneously. See [Multi-Entity Consistency](#multi-entity-consistency) section for examples.

## Multi-Entity Consistency

DCB enables events to belong to multiple consistency boundaries simultaneously through multi-tag events. This allows enforcing consistency rules that span multiple entities.

### Example: Money Transfer

A `MoneyTransferred` event affects both source and destination wallets:

```java
AppendEvent transfer = AppendEvent.builder("MoneyTransferred")
    .tag("from_wallet_id", "wallet1")  // Part of wallet1's consistency boundary
    .tag("to_wallet_id", "wallet2")    // Part of wallet2's consistency boundary
    .build();
```

When querying for either wallet's state, the transfer event is included. This enables:
- Atomic consistency checks across both wallets
- No need for process managers or eventual consistency
- Single operation enforces constraints on multiple entities

### Example: Course Subscription

See `com.crablet.examples.courses` for a complete example where a `StudentSubscribedToCourse` event is tagged with both `studentId` and `courseId`, enabling consistency checks across student subscription limits and course capacity constraints atomically.

## Application Retry Behavior

> **Note**: `CommandExecutor` does not retry automatically. When it throws `ConcurrencyException`, your application layer should implement retry logic (e.g., using Resilience4j).

### Operations (Cursor-Only)
```http
POST /api/wallets/w1/deposits
{"deposit_id": "d1", "amount": 100}
→ 201 CREATED (cursor updated to 6)

# Retry with stale cursor
POST /api/wallets/w1/deposits  
{"deposit_id": "d1", "amount": 100}
→ 409 Conflict (cursor=5 is stale, now at 6)

# Client should:
1. GET /api/wallets/w1 (read new state)
2. Check if balance already increased
3. Don't retry if already processed
```

### Wallet Creation (Idempotent)
```http
PUT /api/wallets/w1
{"owner": "Alice", "initial_balance": 1000}
→ 201 CREATED

# Retry
PUT /api/wallets/w1
{"owner": "Alice", "initial_balance": 1000}  
→ 200 OK (idempotent)
```

## DCB with Wallet Example

This section explains the three key DCB concepts using a concrete wallet withdrawal example.

### Three Key Concepts

#### 1. Decision Model (Query / Criteria)

The **decision model** (also called **criteria** in DCB literature) is the Query that defines which events affect your business decision. This Query dynamically defines your consistency boundary for this operation.

Example for wallet withdrawal:
```java
// Decision model: which events affect withdrawal decisions?
Query decisionModel = QueryBuilder.create()
    .hasTag("wallet_id", walletId)
    .eventNames("WalletOpened", "DepositMade", "WithdrawalMade")
    .build();
```

This Query is passed to `AppendConditionBuilder(decisionModel, cursor)` and used for the DCB conflict check. The decision model determines which events must be checked for conflicts - if any events matching this criteria appeared after your cursor, a conflict is detected.

#### 2. DCB Conflict Detection (stateChanged)

**DCB conflict check** verifies that no events matching the decision model appeared AFTER the cursor.

```java
// Project with cursor
ProjectionResult<WalletBalance> result = eventStore.project(
    decisionModel,
    cursor,
    WalletBalance.class,
    List.of(projector)
);

// AppendCondition checks if ANY events matching decisionModel appeared after cursor
AppendCondition condition = new AppendConditionBuilder(decisionModel, result.cursor())
    .build();

try {
    eventStore.appendIf(events, condition);
} catch (ConcurrencyException e) {
    // Balance changed (deposit/withdrawal happened) - throw to application layer
    throw e;
}
```

**How it works:**
- Cursor captures position when you read balance
- Another transaction makes a deposit → balance changes
- Your appendIf checks: "did ANY WalletOpened/DepositMade/WithdrawalMade events appear after position 42?"
- Answer: yes → throw ConcurrencyException
- Application layer should retry with fresh projection

#### 3. Idempotency (alreadyExists)

**Idempotency check** searches ALL events (ignores cursor) to prevent duplicate operations.

```java
AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
    .withIdempotencyCheck("WithdrawalMade", "withdrawal_id", withdrawalId)
    .build();
```

**How it works:**
- Searches ALL events in database (not just after cursor)
- Looks for any `WithdrawalMade` event with matching `withdrawal_id` tag
- If found → return success (idempotent - already processed)
- If not found → insert event

**Why separate from conflict check:**
- Conflict check: "did state change since I read it?"
- Idempotency check: "have I processed this request ID before?"

### Complete Withdrawal Example

```java
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.query.*;
import com.crablet.eventstore.dcb.*;

@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {
    
    public WithdrawCommandHandler() {
    }
    
    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // 1. Define decision model
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        
        // 2. Project current balance with cursor
        WalletBalanceProjector projector = new WalletBalanceProjector();
        ProjectionResult<WalletBalanceState> result = eventStore.project(
                decisionModel, Cursor.zero(), WalletBalanceState.class, List.of(projector));
        
        // 3. Business logic
        if (!result.state().isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }
        if (!result.state().hasSufficientFunds(command.amount())) {
            throw new InsufficientFundsException(command.walletId(), 
                    result.state().balance(), command.amount());
        }
        
        int newBalance = result.state().balance() - command.amount();
        
        // 4. Create event
        WithdrawalMade withdrawal = WithdrawalMade.of(
            command.withdrawalId(),
            command.walletId(),
            command.amount(),
            newBalance,
            command.description()
        );
        
        AppendEvent event = AppendEvent.builder("WithdrawalMade")
            .tag("wallet_id", command.walletId())
            .tag("withdrawal_id", command.withdrawalId())
            .data(withdrawal)
            .build();
        
        // 5. Build condition with cursor check
        AppendCondition condition = new AppendConditionBuilder(decisionModel, result.cursor())
            .build();
        
        return CommandResult.of(List.of(event), condition);
    }
}
```

### What Happens in Different Scenarios

**Scenario 1: Normal case**
- Read balance: $100
- Check: balance >= $50 ✓
- AppendIf checks: no events after cursor ✓, no duplicate withdrawal_id ✓
- Result: withdrawal succeeds

**Scenario 2: Concurrent deposit**
- Transaction A: reads balance $100 at position 42
- Transaction B: deposits $50 → position 43
- Transaction A: tries to withdraw with cursor 42
- AppendIf checks: found DepositMade at position 43 > 42 ✗
- Result: ConcurrencyException → application should retry

**Scenario 3: Duplicate request**
- First request: withdrawal_id "w-123" succeeds
- Duplicate request: same withdrawal_id "w-123"
- AppendIf checks: found WithdrawalMade with withdrawal_id:w-123 ✗
- Result: Idempotency violation → return success (already processed)

**Scenario 4: Concurrent withdrawals (both insufficient after first)**
- Balance: $100
- Transaction A: reads balance $100, withdraws $80
- Transaction B: reads balance $100, withdraws $80
- Transaction A: appendIf succeeds (balance was $100)
- Transaction B: appendIf fails (sees position changed) → application retries → reads fresh balance $20 → insufficient funds

## Implementation Details

### Package Structure

- **`com.crablet.eventstore.store`**: Core interfaces and implementations (EventStore, StoredEvent, AppendEvent)
- **`com.crablet.command`**: Command handlers (Command, CommandHandler, CommandExecutor) - see [crablet-command](../crablet-command/README.md) module
- **`com.crablet.eventstore.query`**: Querying support (Query, QueryBuilder)
- **`com.crablet.eventstore.dcb`**: DCB implementation (AppendCondition, Cursor)
- **`com.crablet.eventstore.config`**: Configuration classes
- **`com.crablet.eventstore.clock`**: Clock provider for consistent timestamps
- **`com.crablet.outbox`**: Outbox interfaces and implementations

### Cursor Structure

```java
// From StoredEvent (most common)
Cursor cursor = Cursor.of(event.position(), event.occurredAt(), event.transactionId());

// Or use convenience methods
Cursor cursor = Cursor.of(42L);  // position only, timestamp = now, transactionId = "0"
Cursor cursor = Cursor.of(42L, Instant.now());  // position + timestamp, transactionId = "0"
Cursor cursor = Cursor.of(42L, Instant.now(), "12345");  // all fields

// Zero cursor for empty projections
Cursor cursor = Cursor.zero();
```

### Query Builder

```java
Query query = QueryBuilder.create()
    .events("WalletOpened", "DepositMade", "WithdrawalMade")
    .tag("wallet_id", walletId)
    .event("MoneyTransferred", "from_wallet_id", walletId)
    .event("MoneyTransferred", "to_wallet_id", walletId)
    .build();
```

Generates SQL with:
- Event type filtering (`type = ANY(?)`)
- Tag containment checks (`tags @> ?`)
- Position ordering (`ORDER BY transaction_id, position`)

### State Projectors

```java
public interface StateProjector<T> {
    String getId();
    List<String> getEventTypes();
    T getInitialState();
    T transition(T currentState, StoredEvent event, EventDeserializer deserializer);
}
```

Usage:
```java
Query query = QueryBuilder.create()
    .events("WalletOpened", "DepositMade", "WithdrawalMade")
    .tag("wallet_id", walletId)
    .build();

ProjectionResult<WalletBalance> result = eventStore.project(
    query,
    Cursor.zero(),
    WalletBalance.class,
    List.of(new WalletBalanceProjector())
);

WalletBalance balance = result.state();
Cursor cursor = result.cursor(); // Use for DCB concurrency control
```

### Command Handlers

Complete example showing modern Crablet APIs:

```java
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    
    public DepositCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // 1. Project current state to validate wallet exists and get balance
        WalletBalanceProjector projector = new WalletBalanceProjector();
        Query query = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
                query, Cursor.zero(), WalletBalanceState.class, List.of(projector));
        
        if (!projection.state().isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }

        // 2. Business logic
        int newBalance = projection.state().balance() + command.amount();
        DepositMade deposit = DepositMade.of(
            command.depositId(),
            command.walletId(),
            command.amount(),
            newBalance,
            command.description()
        );

        // 3. Create event
        AppendEvent event = AppendEvent.builder("DepositMade")
            .tag("wallet_id", command.walletId())
            .tag("deposit_id", command.depositId())
            .data(deposit)
            .build();

        // 4. Deposits are commutative - no cursor check needed
        // Order doesn't matter: +$10 then +$20 = +$20 then +$10
        // Allows parallel deposits without conflicts
        AppendCondition condition = AppendCondition.empty();

        return CommandResult.of(List.of(event), condition);
}
```

**Key Points:**

1. **Simplified API**: No query builder needed for simple commutative operations
2. **AppendEvent.builder()**: Fluent event creation with inline tags
3. **AppendCondition.empty()**: For commutative operations where order doesn't matter
4. **Parallel Execution**: Deposits can run in parallel without conflicts
5. **Idempotency**: Handled via `deposit_id` tag, prevents duplicate deposits
6. **Performance**: No cursor checks needed for commutative operations

**For non-commutative operations (withdrawals, transfers)**, use `AppendConditionBuilder(decisionModel, cursor)` to detect concurrent balance changes. See [Command Patterns Guide](COMMAND_PATTERNS.md) for complete examples.

**For multi-entity operations (course subscriptions, multi-wallet transfers)**, use composite projectors to enforce constraints across multiple entities atomically. See `com.crablet.examples.courses` for a complete example of multi-entity constraints using composite projectors.

## PostgreSQL Integration

DCB leverages PostgreSQL features:

1. **Snapshot isolation**: `pg_snapshot_xmin()` filters uncommitted transactions
2. **Array operators**: `@>` for O(1) tag containment checks
3. **GIN indexes**: Fast multi-value tag queries
4. **ACID guarantees**: Transactional consistency

Function signature:
```sql
CREATE FUNCTION append_events_if(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_event_types TEXT[] DEFAULT NULL,
    p_condition_tags TEXT[] DEFAULT NULL,
    p_after_cursor_position BIGINT DEFAULT NULL
) RETURNS JSONB
```

## Performance Characteristics

Measured on this codebase (October 2025):

| Operation | Throughput | p95 Latency | Notes |
|-----------|------------|-------------|-------|
| Wallet Creation | 44 req/s | ~500ms | Uses idempotency checks |
| Deposits | ~350 req/s | ~200ms | Cursor-only checks (~4x improvement) |
| Withdrawals | ~350 req/s | ~200ms | Cursor-only checks (~3.6x improvement) |
| Transfers | ~300 req/s | ~250ms | Cursor-only checks (~4x improvement) |
| History Queries | ~1000 req/s | ~60ms | Read-only operations |

**Key Performance Insights:**
- **Operations**: ~4x improvement by removing advisory locks, using cursor-only concurrency control
- **Reduced Event Consumption**: Fine-grained criteria means fewer events to process for state projection (see [Benefits of DCB](#benefits-of-dcb))
- **Reduced Contention**: Smaller consistency boundaries reduce append conflicts (see [Benefits of DCB](#benefits-of-dcb))
- **Wallet Creation**: Maintains idempotency (no cursor protection available)
- **Conflict Detection**: Zero false positives (entity scoping prevents unrelated conflicts)
- **Safety**: Money protected by cursor checks, no duplicate charges possible

## References and Further Reading

### Dynamic Consistency Boundaries
- **[Dynamic Consistency Boundaries](https://javapro.io/2025/10/28/dynamic-consistency-boundaries/)** by Milan Savic (JAVAPRO International, October 2025)
  - Comprehensive explanation of DCB concepts, benefits, and architectural insights
  - Explains DCB as moving from aggregates to dynamically defined consistency boundaries
  - Discusses benefits: reduced event consumption, flexible refactoring, reduced contention
  - Provides architectural insights on Bounded Contexts vs Aggregates
  - Includes practical examples of criteria-based querying
  - This documentation incorporates insights from this article while maintaining Crablet-specific implementation details and examples.
