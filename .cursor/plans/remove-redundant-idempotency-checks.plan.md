# Remove Redundant Manual Idempotency Checks

## Overview
Remove redundant manual idempotency checks from command handlers and rely solely on the DCB pattern with `AppendCondition.withIdempotencyCheck()`. This eliminates duplicate database queries and simplifies the code.

## Problem

Currently, three command handlers have **double idempotency checking**:

1. **Manual check** - Query database, return early if duplicate
2. **DCB check** - Same check in `AppendCondition`, throws `ConcurrencyException` if duplicate

This is:
- **Redundant** - DCB will catch duplicates anyway
- **Inefficient** - Extra database query per command
- **Inconsistent** - Different error handling paths

## Current Implementation (Redundant)

### DepositCommandHandler
```java
// Manual check (line 64-66)
if (depositWasAlreadyProcessed(eventStore, command.depositId())) {
    return CommandResult.emptyWithReason("DUPLICATE_DEPOSIT_ID");
}

// DCB check (line 88)
.withIdempotencyCheck("DepositMade", "deposit_id", command.depositId())

// Helper method (line 98-103)
private boolean depositWasAlreadyProcessed(EventStore store, String depositId) {
    Query query = QueryBuilder.create()
        .event("DepositMade", "deposit_id", depositId)
        .build();
    return !store.query(query, null).isEmpty();
}
```

### WithdrawCommandHandler
```java
// Manual check (line 70-72)
if (withdrawalWasAlreadyProcessed(eventStore, command.withdrawalId())) {
    return CommandResult.emptyWithReason("DUPLICATE_WITHDRAWAL_ID");
}

// DCB check (line 94)
.withIdempotencyCheck("WithdrawalMade", "withdrawal_id", command.withdrawalId())

// Helper method (line 104-109)
private boolean withdrawalWasAlreadyProcessed(EventStore store, String withdrawalId) {
    Query query = QueryBuilder.create()
        .event("WithdrawalMade", "withdrawal_id", withdrawalId)
        .build();
    return !store.query(query, null).isEmpty();
}
```

### TransferMoneyCommandHandler
```java
// Manual check (line 75-77)
if (transferWasAlreadyProcessed(eventStore, command.transferId())) {
    return CommandResult.emptyWithReason("DUPLICATE_TRANSFER_ID");
}

// DCB check (line 105)
.withIdempotencyCheck("MoneyTransferred", "transfer_id", command.transferId())

// Helper method (line 115-120)
private boolean transferWasAlreadyProcessed(EventStore store, String transferId) {
    Query query = QueryBuilder.create()
        .event("MoneyTransferred", "transfer_id", transferId)
        .build();
    return !store.query(query, null).isEmpty();
}
```

## Correct Implementation (DCB Only)

### OpenWalletCommandHandler (Already Correct!)
```java
// NO manual check - relies entirely on DCB

// DCB check only (line 62-64)
Query existenceQuery = WalletQueryPatterns.walletExistenceQuery(command.walletId());
AppendCondition condition = existenceQuery
    .toAppendCondition(Cursor.zero())
    .build();

// Duplicates caught by ConcurrencyException
// GlobalExceptionHandler returns proper 409 response
```

## Changes Needed

### 1. DepositCommandHandler
- **Remove** manual check (lines 63-66)
- **Remove** `depositWasAlreadyProcessed()` method (lines 95-103)
- **Keep** DCB idempotency check in `AppendCondition`

### 2. WithdrawCommandHandler
- **Remove** manual check (lines 70-72)
- **Remove** `withdrawalWasAlreadyProcessed()` method (lines 101-109)
- **Keep** DCB idempotency check in `AppendCondition`

### 3. TransferMoneyCommandHandler
- **Remove** manual check (lines 75-77)
- **Remove** `transferWasAlreadyProcessed()` method (lines 112-120)
- **Keep** DCB idempotency check in `AppendCondition`

### 4. Update Documentation
- Update command handler example in `docs/architecture/DCB_AND_CRABLET.md`
- **Remove** the manual idempotency check section (lines 44-47)
- Show that DCB handles idempotency automatically

## Error Handling

When a duplicate is attempted, the flow will be:

1. **DCB detects duplicate** - `AppendCondition.withIdempotencyCheck()` fails
2. **JDBCEventStore throws** - `ConcurrencyException` with command details
3. **GlobalExceptionHandler catches** - Returns 409 with proper error message

Already handled in `GlobalExceptionHandler.handleConcurrencyConflict()`:
```java
if (cmdType.equals("deposit")) {
    return handleDuplicateOperation(new DuplicateOperationException("Deposit already processed"));
}
```

## Benefits

1. **Single Responsibility** - DCB pattern handles all concurrency control
2. **Performance** - One less database query per command
3. **Consistency** - All duplicates handled the same way
4. **Simpler Code** - Remove 3 helper methods, 3 manual checks
5. **Better Pattern** - Follows DCB principle: "check once, atomically"

## Testing

All existing tests will continue to pass:
- Idempotency tests verify duplicates are rejected (via ConcurrencyException)
- GlobalExceptionHandler tests verify proper 409 responses
- No behavior change from external perspective

Run:
```bash
./mvnw test
```

## Files to Modify

1. `src/main/java/com/wallets/features/deposit/DepositCommandHandler.java`
2. `src/main/java/com/wallets/features/withdraw/WithdrawCommandHandler.java`
3. `src/main/java/com/wallets/features/transfer/TransferMoneyCommandHandler.java`
4. `docs/architecture/DCB_AND_CRABLET.md` - Update command handler example

