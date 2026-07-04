# Dynamic Consistency Boundary (DCB)

## Quick Reference

**DCB in 60 seconds:**

DCB redefines consistency granularity in event-sourced systems, moving from fixed aggregates to dynamically defined consistency boundaries based on criteria (queries).

**How it works:**
1. Read current state, capture stream position
2. Generate events based on read state
3. Server checks for conflicts before writing (streamPosition-based)
4. If conflict detected → `ConcurrencyException`, application retries

**Key Benefits:**

| Benefit | Description |
|---------|-------------|
| **Reduces event consumption** | Fine-grained filtering means only process relevant events |
| **Eliminates eventual consistency** | Business rules spanning multiple aggregates enforced immediately |
| **Reduces contention** | Smaller boundaries = less conflicts = better parallelism |
| **Flexible refactoring** | Adjust boundaries without stream restructuring |
| **Less upfront design pressure** | Refine boundaries as you learn |

**Performance:** streamPosition-based checks (`appendNonCommutative`) and duplicate checks
(`appendIdempotent`) both use an advisory lock internally (distinctly keyed, so they never
serialize against each other); overhead is comparable between the two.

📖 **Details:** See [sections below](#what-is-dcb).

## Problem Statement

Event sourcing systems require concurrency control to prevent inconsistent state. Example scenario:

- Wallet balance: $1000
- Request A: Transfer $600 (reads position 42)
- Request B: Transfer $500 (reads position 42)
- Both see sufficient balance, both succeed → balance: -$100 (invalid)

## What is DCB?

Dynamic Consistency Boundaries (DCB) redefine consistency granularity in event-sourced systems, moving from fixed aggregates (event streams) to dynamically defined consistency boundaries based on criteria (queries).

### Traditional vs DCB Approach

| Aspect | Traditional (Aggregates) | DCB (Criteria-Based) |
|--------|------------------------|---------------------|
| **Consistency Boundary** | Aggregate boundary | Events matching criteria (decision model) |
| **Boundary Definition** | Fixed per stream | Dynamic per operation |
| **Refactoring** | Requires stream restructuring | Change criteria, stream stays intact |
| **Multi-Aggregate Rules** | Requires eventual consistency | Immediate consistency via multi-entity criteria |
| **Event Reuse** | One stream per aggregate | Single event can belong to multiple boundaries |

**Broader Applicability:** DCB applies to any messaging system with append-only log and pub/sub characteristics, not limited to event sourcing.

### Architectural Insight: Bounded Context vs Aggregate

When organizing event streams, consider aligning streams with **Bounded Contexts** (sub-domains) rather than individual Aggregates. Within a Bounded Context, you can use DCB to maintain flexible consistency boundaries:

- **Bounded Context scope**: Store all events for a sub-domain in one stream
- **DCB within context**: Define consistency boundaries dynamically per operation
- **Benefit**: More flexible than strict aggregate boundaries, easier to refactor

This is architectural guidance, not a requirement. DCB works regardless of how you organize your streams.

## DCB Mechanism

DCB uses streamPosition-based checks to detect conflicting events since last read.

### Implementation Steps

1. **Read**: Client reads current state, captures stream position
2. **Compute**: Client generates events based on read state
3. **Append**: Server checks for conflicts before writing
4. **Exception**: If conflict detected, server throws `ConcurrencyException`, application retries from step 1

### Conflict Detection Query

```sql
SELECT COUNT(*) FROM crablet_events 
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

## Crablet's Concurrency Strategies

DCB defines the core mechanism: criteria-based consistency boundaries and streamPosition-based optimistic locking. Crablet builds on this with **three append methods** that cover the common operational patterns, each with different concurrency semantics.

> These method names (`appendNonCommutative`, `appendCommutative`, `appendIdempotent`) are Crablet's API — not DCB spec vocabulary. The underlying streamPosition conflict detection is DCB; the three-way taxonomy is Crablet's implementation decision.

| Aspect | `appendNonCommutative` | `appendCommutative` | `appendIdempotent` |
|--------|-------------------|-------------------|-------------------|
| **Use Case** | State-dependent operations (Withdraw, Transfer) | Order-independent operations (Deposit, Credit) | Entity creation (OpenWallet) |
| **What It Checks** | "Has anything changed AFTER stream position X?" | Nothing (or optional lifecycle guard) | "Does entity already exist?" |
| **Advisory Locks** | ✅ Required (decision-model-keyed) | ❌ Not needed | ✅ Required (idempotency-keyed) |
| **Protection Mechanism** | Advisory lock (decision-model-keyed) + snapshot conflict check | Optional lifecycle guard | Advisory lock (idempotency-keyed) serializes duplicate checks |
| **Performance** | Comparable to `appendIdempotent`'s lock overhead; independent of caller isolation level | Fastest (no conflict check) | Same lock mechanism, independent hash namespace |
| **Behavior** | 201 CREATED on success, 409 Conflict on stale position | Always succeeds (order-independent) | 201 CREATED for new entities, 200 OK for duplicates |

**Key Insight**: `appendNonCommutative`'s DCB conflict check is snapshot-based
(`transaction_id < pg_snapshot_xmin(...)`), which alone only detects *staggered* races — if writer
A's transaction commits before writer B's conflict check runs, B correctly sees the conflict.
Under plain `READ_COMMITTED`, two *genuinely simultaneous* writers racing the same decision model
could otherwise both pass the check before either commits and both succeed, silently violating the
DCB guarantee. `append_events_if()` closes this window with a `pg_advisory_xact_lock` keyed on the
decision model's event types/tags (distinctly namespaced from the idempotency lock, so the two
checks never collide or needlessly serialize against each other) — the same mechanism
`appendIdempotent` already used, now extended to the concurrency check too. Because the lock lives
inside the SQL function itself, this protection is uniform regardless of caller isolation level or
whether the append runs standalone or inside a command-handler transaction.

### Commutative Operations and Lifecycle Guards

Some operations are order-independent with respect to each other (e.g., two concurrent deposits do not conflict) but still require the entity to be in an active state (e.g., the wallet must be open). Crablet provides `CommutativeGuarded` for this case:

- Concurrent operations of the same type (e.g., two deposits) **do not** conflict with each other — Crablet calls `appendCommutative` with no stream-position check between them.
- A **lifecycle guard query** — containing only lifecycle event types such as `WalletOpened`/`WalletClosed`, **not** `DepositMade` — is checked before appending. If a lifecycle event appeared after the projected position, a `ConcurrencyException` is thrown.

This gives the best of both worlds: high parallelism for the common case, with protection against operations on a closed/deleted entity.

## Entity Scoping

DCB checks are scoped to specific entities via tags:

```java
AppendCondition condition = AppendCondition.builder()
    .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred")
    .tags("wallet_id", walletId)
    .afterPosition(streamPosition)
    .build();
```

Result: Conflicts only detected for events affecting same wallet. Operations on wallet A don't block operations on wallet B.

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

**For more complex examples**, see Course Subscriptions (`com.crablet.examples.course`) which demonstrates capacity limits, subscription limits, and duplicate checks using composite projectors.

## Application Retry Behavior

> **Note**: `CommandExecutor` does not retry automatically. When it throws `ConcurrencyException`, your application layer should implement retry logic (e.g., using Resilience4j).

### Operations (StreamPosition-Only)

```http
POST /api/wallets/w1/deposits
{"deposit_id": "d1", "amount": 100}
→ 201 CREATED (stream position updated to 6)

# Retry with stale stream position
POST /api/wallets/w1/deposits  
{"deposit_id": "d1", "amount": 100}
→ 409 Conflict (streamPosition=5 is stale, now at 6)

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
Query decisionModel = QueryBuilder.builder()
    .hasTag("wallet_id", walletId)
    .eventNames("WalletOpened", "DepositMade", "WithdrawalMade")
    .build();
```

This Query is passed to `AppendConditionBuilder(decisionModel, streamPosition)` and used for the DCB conflict check.

#### 2. DCB Conflict Detection (concurrencyQuery)

**DCB conflict check** verifies that no events matching the decision model appeared AFTER the stream position.

```java
// Project with stream position
ProjectionResult<WalletBalance> result = eventStore.project(
    decisionModel, streamPosition, WalletBalance.class, List.of(projector)
);

// AppendCondition checks if ANY events matching decisionModel appeared after stream position
AppendCondition condition = AppendConditionBuilder.of(decisionModel, result.streamPosition())
    .build();

try {
    String transactionId = eventStore.appendNonCommutative(events, decisionModel, result.streamPosition());
} catch (ConcurrencyException e) {
    // Balance changed - throw to application layer for retry
    throw e;
}
```

**How it works:**
- StreamPosition captures position when you read balance
- Another transaction makes a deposit → balance changes
- `appendNonCommutative` checks: "did ANY WalletOpened/DepositMade/WithdrawalMade events appear after position 42?"
- Answer: yes → throw ConcurrencyException
- Application layer should retry with fresh projection

#### 3. Idempotency (idempotencyQuery)

**Idempotency check** searches ALL events (ignores stream position) to prevent duplicate operations.

```java
AppendCondition condition = AppendConditionBuilder.of(decisionModel, streamPosition)
    .withIdempotencyCheck(type(WithdrawalMade.class), "withdrawal_id", withdrawalId)
    .build();
```

**How it works:**
- Searches ALL events in database (not just after stream position)
- Looks for any `WithdrawalMade` event with matching `withdrawal_id` tag
- If found → return success (idempotent - already processed)
- If not found → insert event

**Why separate from conflict check:**
- Conflict check: "did state change since I read it?"
- Idempotency check: "have I processed this request ID before?"

### Complete Withdrawal Example

```java
@Component
public class WithdrawCommandHandler implements NonCommutativeCommandHandler<WithdrawCommand> {
    
    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, WithdrawCommand command) {
        // 1. Define decision model
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        
        // 2. Project current balance with stream position
        WalletBalanceStateProjector projector = new WalletBalanceStateProjector();
        ProjectionResult<WalletBalanceState> result = eventStore.project(decisionModel, projector);
        
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
            command.withdrawalId(), command.walletId(), command.amount(),
            newBalance, command.description()
        );
        
        AppendEvent event = AppendEvent.builder("WithdrawalMade")
            .tag("wallet_id", command.walletId())
            .tag("withdrawal_id", command.withdrawalId())
            .data(withdrawal)
            .build();
        
        // 5. Return non-commutative decision with stream position for conflict detection
        return CommandDecision.NonCommutative.of(event, decisionModel, result.streamPosition());
    }
}
```

### What Happens in Different Scenarios

**Scenario 1: Normal case**
- Read balance: $100
- Check: balance >= $50 ✓
- appendNonCommutative checks: no events after stream position ✓
- Result: withdrawal succeeds

**Scenario 2: Concurrent deposit**
- Transaction A: reads balance $100 at position 42
- Transaction B: deposits $50 → position 43
- Transaction A: tries to withdraw with stream position 42
- appendNonCommutative checks: found DepositMade at position 43 > 42 ✗
- Result: ConcurrencyException → application should retry

**Scenario 3: Concurrent withdrawals**
- Balance: $100
- Transaction A: reads balance $100, withdraws $80
- Transaction B: reads balance $100, withdraws $80
- Transaction A: appendNonCommutative succeeds (balance was $100)
- Transaction B: appendNonCommutative fails (sees position changed) → application retries → reads fresh balance $20 → insufficient funds

## Implementation Details

### StreamPosition Structure

```java
// From StoredEvent (most common)
StreamPosition streamPosition = StreamPosition.of(event.position(), event.occurredAt(), event.transactionId());

// Or use convenience methods
StreamPosition streamPosition = StreamPosition.of(42L, clock.instant(), "0");  // explicit position + clock-provided timestamp
StreamPosition streamPosition = StreamPosition.zero();  // zero stream position for empty projections
```

### Query Builder

```java
Query query = QueryBuilder.builder()
    .events("WalletOpened", "DepositMade", "WithdrawalMade")
    .tag("wallet_id", walletId)
    .event("MoneyTransferred", "from_wallet_id", walletId)
    .event("MoneyTransferred", "to_wallet_id", walletId)
    .build();
```

Generates SQL with event type filtering, tag containment checks, and position ordering.

### Event Type Name Contract

Crablet stores event type names as strings. The supported convention is that event type strings come
from `EventType.type(Class)`, which returns the event class simple name. That simple name is durable
application data: it appears in `crablet_events.type`, query filters, projector registrations,
processor subscriptions, and Jackson `@JsonSubTypes` mappings.

Do not rename an event class after events may have been stored for it. A rename creates a new event
type from Crablet's point of view and can make historical events invisible to projectors or
processors. If the domain language changes, add a new event class and teach readers to handle both
old and new event types during the migration period.

For application CI, add an opt-in Jackson subtype alignment test with `crablet-test-support`:

```java
EventTypeContract.assertJsonSubTypesMatchEventType(WalletEvent.class);
```

### State Projectors

```java
public interface StateProjector<T> {
    default String getId() { return getClass().getSimpleName(); }
    List<String> getEventTypes();
    T getInitialState();
    T transition(T currentState, StoredEvent event, EventDeserializer deserializer);
}
```

Usage:
```java
Query query = QueryBuilder.builder()
    .events("WalletOpened", "DepositMade", "WithdrawalMade")
    .tag("wallet_id", walletId)
    .build();

ProjectionResult<WalletBalance> result = eventStore.project(query, new WalletBalanceStateProjector());

WalletBalance balance = result.state();
StreamPosition streamPosition = result.streamPosition(); // Use for DCB concurrency control
```

## PostgreSQL Integration

DCB leverages PostgreSQL features:

1. **Snapshot isolation**: `pg_snapshot_xmin()` filters uncommitted transactions
2. **GIN index on `crablet_events.tags`**: idempotency and DCB conflict checks use `crablet_events.tags @>` containment queries — real decision models use 2+ tags per criterion, so GIN handles the common case directly
3. **`crablet_event_tags` B-tree indexes**: per-processor poller tag filtering uses correlated EXISTS subqueries against the derived `crablet_event_tags` table (one row per `key=value` pair per event) instead of `unnest(tags)` scans. `(key, value, position)` covers exact tag filters; `(key, position)` covers broad key-existence filters such as all events with `wallet_id`.
4. **ACID guarantees**: Transactional consistency

Function signature:
```sql
CREATE FUNCTION append_events_if(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_event_types TEXT[] DEFAULT NULL,
    p_condition_tags TEXT[] DEFAULT NULL,
    p_after_position BIGINT DEFAULT NULL
) RETURNS JSONB
```

## Performance Characteristics

Measured on this codebase (numbers below predate the decision-model-keyed advisory lock added to
`appendNonCommutative`'s concurrency check — re-benchmark before relying on the exact figures;
the qualitative ordering and safety properties still hold):

| Operation | Throughput | p95 Latency | Notes |
|-----------|------------|-------------|-------|
| Wallet Creation | 44 req/s | ~500ms | Uses idempotency checks |
| Deposits | ~350 req/s | ~200ms | StreamPosition-only checks |
| Withdrawals | ~350 req/s | ~200ms | StreamPosition-only checks |
| Transfers | ~300 req/s | ~250ms | StreamPosition-only checks |
| History Queries | ~1000 req/s | ~60ms | Read-only operations |

**Key Performance Insights:**
- **Operations**: `appendNonCommutative` and `appendIdempotent` both use an advisory lock
  internally now (distinctly keyed); neither is lock-free, but the locks never serialize against
  each other
- **Reduced Event Consumption**: Fine-grained criteria means fewer events to process for state projection
- **Reduced Contention**: Smaller consistency boundaries reduce append conflicts
- **Safety**: Money protected by streamPosition checks, no duplicate charges possible

## References and Further Reading

### Dynamic Consistency Boundaries

DCB (Dynamic Consistency Boundary) was **introduced by Sara Pellegrini** in her blog post "Killing the Aggregate".

- **[DCB Official Specification](https://dcb.events/)** - Official DCB website and specification
- **[Dynamic Consistency Boundaries - Presentation](https://www.youtube.com/watch?v=0iP65Durhbs)** by Sara Pellegrini & Milan Savic
- **[Dynamic Consistency Boundaries](https://javapro.io/2025/10/28/dynamic-consistency-boundaries/)** by Milan Savic (JAVAPRO International, October 2025)
