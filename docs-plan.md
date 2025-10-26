# Documentation Plan for New Developers

## Problem

New developers cannot successfully integrate the library because:
1. README examples use wrong API (stream-based vs tag-based)
2. No working end-to-end example
3. Missing package imports
4. No database setup instructions
5. No test examples

## Solution

### 1. Fix README Examples (EventStore)
**Problem**: Examples show `eventStore.append("stream-id", events)` - API doesn't exist
**Fix**: Replace with tag-based DCB examples
- Correct append() signature
- Correct QueryBuilder usage
- Show full working code

### 2. Create GETTING_STARTED.md
**What**: Step-by-step integration guide
**Contents**:
- Add Maven dependency
- Create database schema (SQL script)
- Write first event handler
- Implement first projector
- Run first append and query
- Complete working example

### 3. Add package import examples
**Problem**: Developers don't know which classes to import
**Fix**: Add import statements to all code examples

### 4. Create TESTING.md
**What**: How to write and run tests
**Contents**:
- Testcontainers setup
- EventStore integration test
- Command handler test
- EventTestHelper usage
- Assertion examples

### 5. Document DCB Pattern with Projections
**Problem**: DCB docs are too abstract, missing key concepts
**Fix**: Add concrete examples showing:

**Three key concepts:**
1. **Decision Model** (StateProjector): Business logic that reads events and produces state
2. **DCB Conflict** (AppendCondition): Optimistic locking check - fails if state changed since cursor
3. **AppendIf Idempotency**: WithIdempotencyCheck() prevents duplicate operations

**Complete flow example (Wallet withdrawal):**
- Project wallet balance with cursor
- Check sufficient funds (balance >= amount)
- Create WithdrawalMade event with withdrawal_id tag
- AppendCondition checks: (1) no balance changes since cursor, (2) withdrawal_id doesn't exist
- If conflict (balance changed): catch ConcurrencyException, retry
- If idempotency: withdrawal_id search ignores cursor (checks ALL events)
- AppendIf succeeds only if both checks pass

**Code example with Wallet domain:**
```java
// 1. Project wallet state with cursor
Query query = QueryBuilder.create()
    .hasTag("wallet_id", walletId)
    .eventNames("WalletOpened", "DepositMade", "WithdrawalMade")
    .build();

ProjectionResult<WalletBalance> result = eventStore.project(
    query, 
    cursor,  // from last read
    WalletBalance.class, 
    List.of(balanceProjector)
);
WalletBalance balance = result.state();
Cursor newCursor = result.cursor();

// 2. Business logic - check sufficient funds
if (balance.amount() < withdrawAmount) {
    throw new InsufficientFundsException();
}

// 3. Build condition with DCB conflict check + idempotency
AppendCondition condition = new AppendConditionBuilder(decisionModel, newCursor)
    .withIdempotencyCheck("WithdrawalMade", "withdrawal_id", withdrawalId)  // Checks ALL events
    .build();

List<AppendEvent> events = List.of(
    AppendEvent.builder("WithdrawalMade")
        .tag("wallet_id", walletId)
        .tag("withdrawal_id", withdrawalId)
        .data(new WithdrawalMade(amount))
        .build()
);

// 4. Retry on conflict
try {
    eventStore.appendIf(events, condition);
} catch (ConcurrencyException e) {
    // Balance changed - retry with fresh cursor
    retry();
}
```

## Implementation Order

1. Fix EventStore README examples (remove stream-based code)
2. Add package imports to all code examples  
3. Create GETTING_STARTED.md with working example
4. Create TESTING.md with Testcontainers setup
5. Enhance DCB docs with concrete examples

## Time: 4-6 hours

## Success Criteria

New developer can:
1. Add library to project in <5 minutes
2. Create database schema
3. Write and test event handler
4. Query and project state
5. Run existing tests
