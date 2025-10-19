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

### Cursor Structure

```java
Cursor cursor = Cursor.of(
    lastEvent.position(),      // Event stream position
    lastEvent.occurredAt(),    // Timestamp
    lastEvent.transactionId()  // PostgreSQL transaction ID
);
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

## Use Cases

Applicable to:
- Banking/financial operations
- Inventory management
- Order processing
- Any domain where entity-level consistency required

Not applicable to:
- Append-only logs
- Read-only analytics
- Single-user systems

## Related Implementations

- go-crablet (Go)
- crablet (Kotlin)
- spring-crablet (Java 25, this implementation)

Pattern is language-agnostic; requires:
- Event store with append operations
- Database with ACID guarantees
- Position-based event ordering
