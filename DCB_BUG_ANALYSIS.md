# DCB Cursor Violation Bug Analysis

## Summary

The `append_events_if` PostgreSQL function has a critical bug that prevents it from detecting concurrent modifications,
violating DCB optimistic locking guarantees.

## Root Cause

**File**: `src/main/resources/db/migration/V1__go_crablet_schema.sql`  
**Line**: 85

```sql
AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())
```

This line filters out events from transactions that started AFTER the current transaction's snapshot was taken.

## The Problem

### Current Behavior (WRONG ❌)

1. Transaction T1 projects state and gets cursor at position 10
2. Transaction T2 appends event at position 11 and commits
3. Transaction T3 calls `appendIf` with cursor from step 1
4. **BUG**: T3's `pg_current_snapshot()` doesn't include T2's events because T2 committed after T3 started
5. **RESULT**: No cursor violation is detected, event is appended (LOST UPDATE!)

### Expected Behavior (CORRECT ✅)

1. Transaction T1 projects state and gets cursor at position 10
2. Transaction T2 appends event at position 11 and commits
3. Transaction T3 calls `appendIf` with cursor from step 1
4. **CORRECT**: Check finds event at position 11 (after cursor position 10)
5. **RESULT**: Cursor violation detected, `ConcurrencyException` thrown

## Test Evidence

**Test**: `MinimalDCBTest.shouldDetectCursorViolation()`

```java
// 1. Append event1, get cursor
// 2. Append event2 (concurrent modification)
// 3. Try appendIf with stale cursor
// EXPECTED: ConcurrencyException
// ACTUAL: Success (BUG!)
```

**Result**: ❌ Test fails - no exception thrown

## Impact

This bug means:

- ✅ Idempotency checks work (duplicate operation IDs are detected)
- ❌ **Optimistic locking DOESN'T work** (concurrent modifications are missed)
- ❌ **Lost updates are possible** (race conditions lead to incorrect state)
- ❌ **DCB guarantees are violated** (not truly using Dynamic Consistency Boundary)

## Why This is Critical

For a financial system like the wallet application:

- Two concurrent withdrawals could both succeed when balance is insufficient
- Race conditions in transfers could lead to money creation/destruction
- Audit trail becomes unreliable

## The Fix

### Option 1: Remove `pg_snapshot_xmin` Filter (Recommended)

```sql
-- BEFORE (line 85):
AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())

-- AFTER:
-- (remove the line entirely)
```

**Rationale**: We want to check ALL committed events, not just those visible in our snapshot.

### Option 2: Use READ COMMITTED Isolation

Ensure the query sees all committed transactions, not just snapshot-visible ones. However, this doesn't solve the
fundamental issue.

### Option 3: Use Advisory Locks

Add pessimistic locking for the projection+append operation. This changes the concurrency model entirely.

## Recommendation

**Implement Option 1** - Remove the `pg_snapshot_xmin` filter.

The check should be:

```sql
WHERE (
    (p_event_types IS NULL OR e.type = ANY(p_event_types))
    AND
    (p_condition_tags IS NULL OR e.tags @> p_condition_tags)
)
AND (p_after_cursor_tx_id IS NULL OR
     (e.transaction_id > p_after_cursor_tx_id) OR
     (e.transaction_id = p_after_cursor_tx_id AND e.position > p_after_cursor_position))
-- REMOVED: AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())
LIMIT 1;
```

This will check for ANY events after the cursor, regardless of transaction visibility in the current snapshot.

## Testing

After the fix, all 16 DCB compliance tests should pass:

- ✅ Atomicity tests (3 tests)
- ✅ Ordering tests (4 tests)
- ✅ Integrity tests (5 tests)
- ✅ Wallet domain tests (4 tests)

## Next Steps

1. Create migration to fix `append_events_if` function
2. Run all DCB tests to verify fix
3. Run full test suite to ensure no regressions
4. Document the DCB guarantees provided
5. Consider adding more edge case tests

