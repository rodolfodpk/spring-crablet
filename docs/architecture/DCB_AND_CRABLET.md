# Dynamic Consistency Boundary (DCB) Pattern

## Problem Statement

Event sourcing systems require concurrency control to prevent inconsistent state. Example scenario:

- Wallet balance: $1000
- Request A: Transfer $600 (reads position 42)
- Request B: Transfer $500 (reads position 42)
- Both see sufficient balance, both succeed → balance: -$100 (invalid)

## DCB Mechanism

DCB uses cursor-based checks to detect conflicting events since last read.

### Implementation Steps

1. **Read**: Client reads current state, captures cursor position
2. **Compute**: Client generates events based on read state
3. **Append**: Server checks for conflicts before writing
4. **Retry**: If conflict detected, client retries from step 1

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
Request B: Read@42 → Check → Conflict (position 43 exists) → 409
           Retry: Read@43 → Check → Write@44 (success)
```

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

## Implementation Details

### Package Structure

- **`com.crablet.core`**: Framework-agnostic interfaces (EventStore, CommandExecutor)
- **`com.crablet.impl`**: Spring Boot implementations (JDBCEventStore, DefaultCommandExecutor)

### Cursor Structure

```java
// From StoredEvent (most common)
Cursor cursor = Cursor.from(lastEvent).build();

// Or build from scratch
Cursor cursor = Cursor.builder()
    .position(42L)
    .occurredAt(Instant.now())
    .transactionId("12345")
    .build();
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

### State Projector Pattern

```java
public interface StateProjector<T> {
    T transition(T currentState, StoredEvent event);
    T getInitialState();
    Query getQuery(String entityId);
}
```

Usage:
```java
List<StoredEvent> events = eventStore.query(query, cursor);
WalletState state = projector.project(events);
```

### Command Handler Pattern

Complete example showing modern Crablet APIs:

```java
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    
    private final ObjectMapper objectMapper;
    private final WalletBalanceProjector balanceProjector;

    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // 1. Define decision model query (what state do we need?)
        Query decisionModel = QueryBuilder.create()
            .events("WalletOpened", "DepositMade", "WithdrawalMade")
            .tag("wallet_id", command.walletId())
            .event("MoneyTransferred", "from_wallet_id", command.walletId())
            .event("MoneyTransferred", "to_wallet_id", command.walletId())
            .build();

        // 2. Project current state
        ProjectionResult<WalletBalanceState> projection =
            balanceProjector.projectWalletBalance(eventStore, command.walletId(), decisionModel);
        
        if (!projection.state().isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }

        // 3. Idempotency check
        if (depositWasAlreadyProcessed(eventStore, command.depositId())) {
            return CommandResult.emptyWithReason("DUPLICATE_DEPOSIT_ID");
        }

        // 4. Business logic
        int newBalance = projection.state().balance() + command.amount();
        DepositMade deposit = DepositMade.of(
            command.depositId(),
            command.walletId(),
            command.amount(),
            newBalance,
            command.description()
        );

        // 5. Create event with fluent builder
        AppendEvent event = AppendEvent.builder("DepositMade")
            .tag("wallet_id", command.walletId())
            .tag("deposit_id", command.depositId())
            .data(serializeEvent(objectMapper, deposit))
            .build();

        // 6. Build append condition (DCB enforcement)
        AppendCondition condition = decisionModel
            .toAppendCondition(projection.cursor())
            .build();

        return CommandResult.of(List.of(event), condition);
    }

    private boolean depositWasAlreadyProcessed(EventStore store, String depositId) {
        Query query = QueryBuilder.create()
            .event("DepositMade", "deposit_id", depositId)
            .build();
        return !store.query(query, null).isEmpty();
    }
}
```

**Key Points:**

1. **QueryBuilder**: Fluent API for building complex queries
2. **AppendEvent.builder()**: Fluent event creation with inline tags
3. **AppendCondition**: Enforces concurrency control using decision model cursor
4. **Manual Idempotency**: Separate checks for duplicate operations (200 OK)
5. **DCB Principle**: Condition uses same query as projection for consistency

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

| Operation | Throughput | p95 Latency |
|-----------|------------|-------------|
| Wallet Creation | 723 req/s | 42ms |
| Transfers | 224 req/s | 94ms |
| 50 Concurrent Users | 87 req/s | 793ms |

Conflict detection: Zero false positives (entity scoping prevents unrelated conflicts)
