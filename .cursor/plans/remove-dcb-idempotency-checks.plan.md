# Remove Redundant DCB Idempotency Checks

## Overview
Remove redundant DCB idempotency checks from command handlers while keeping manual idempotency checks. This maintains correct idempotency semantics (200 OK for duplicates) while eliminating redundant database queries.

## Problem Analysis

Currently, three command handlers have **redundant idempotency checking**:

1. **Manual check** - Query database, return 200 OK if duplicate (✅ **Correct for idempotency**)
2. **DCB check** - Same check in `AppendCondition` (❌ **Redundant**)

The manual checks are **correctly implementing idempotency**:
- Same `depositId` → Same result → 200 OK
- This is the **expected behavior** for idempotent operations

The DCB `.withIdempotencyCheck()` is **redundant** because:
- If manual check passes, we know there's no duplicate
- DCB check will never find a duplicate (manual check already verified)
- DCB is only needed for **concurrency control** (state changes during processing)

## Current Implementation (Redundant)

### DepositCommandHandler
```java
// Manual check (line 64-66) - KEEP THIS
if (depositWasAlreadyProcessed(eventStore, command.depositId())) {
    return CommandResult.emptyWithReason("DUPLICATE_DEPOSIT_ID"); // 200 OK
}

// DCB check (line 88) - REMOVE THIS
.withIdempotencyCheck("DepositMade", "deposit_id", command.depositId())
```

### WithdrawCommandHandler
```java
// Manual check (line 70-72) - KEEP THIS
if (withdrawalWasAlreadyProcessed(eventStore, command.withdrawalId())) {
    return CommandResult.emptyWithReason("DUPLICATE_WITHDRAWAL_ID"); // 200 OK
}

// DCB check (line 94) - REMOVE THIS
.withIdempotencyCheck("WithdrawalMade", "withdrawal_id", command.withdrawalId())
```

### TransferMoneyCommandHandler
```java
// Manual check (line 75-77) - KEEP THIS
if (transferWasAlreadyProcessed(eventStore, command.transferId())) {
    return CommandResult.emptyWithReason("DUPLICATE_TRANSFER_ID"); // 200 OK
}

// DCB check (line 105) - REMOVE THIS
.withIdempotencyCheck("MoneyTransferred", "transfer_id", command.transferId())
```

## Correct Implementation (Manual Only)

### DepositCommandHandler
```java
// Keep manual idempotency check
if (depositWasAlreadyProcessed(eventStore, command.depositId())) {
    return CommandResult.emptyWithReason("DUPLICATE_DEPOSIT_ID");
}

// Keep DCB for concurrency control only
AppendCondition condition = decisionModel
    .toAppendCondition(projection.cursor())
    .build(); // No idempotency check
```

### WithdrawCommandHandler
```java
// Keep manual idempotency check
if (withdrawalWasAlreadyProcessed(eventStore, command.withdrawalId())) {
    return CommandResult.emptyWithReason("DUPLICATE_WITHDRAWAL_ID");
}

// Keep DCB for concurrency control only
AppendCondition condition = decisionModel
    .toAppendCondition(projection.cursor())
    .build(); // No idempotency check
```

### TransferMoneyCommandHandler
```java
// Keep manual idempotency check
if (transferWasAlreadyProcessed(eventStore, command.transferId())) {
    return CommandResult.emptyWithReason("DUPLICATE_TRANSFER_ID");
}

// Keep DCB for concurrency control only
AppendCondition condition = transferProjection.decisionModel()
    .toAppendCondition(transferProjection.cursor())
    .build(); // No idempotency check
```

## Changes Needed

### 1. DepositCommandHandler
- **Remove** `.withIdempotencyCheck("DepositMade", "deposit_id", command.depositId())` (line 88)
- **Keep** manual `depositWasAlreadyProcessed()` check (lines 64-66)
- **Keep** `depositWasAlreadyProcessed()` helper method (lines 98-103)

### 2. WithdrawCommandHandler
- **Remove** `.withIdempotencyCheck("WithdrawalMade", "withdrawal_id", command.withdrawalId())` (line 94)
- **Keep** manual `withdrawalWasAlreadyProcessed()` check (lines 70-72)
- **Keep** `withdrawalWasAlreadyProcessed()` helper method (lines 104-109)

### 3. TransferMoneyCommandHandler
- **Remove** `.withIdempotencyCheck("MoneyTransferred", "transfer_id", command.transferId())` (line 105)
- **Keep** manual `transferWasAlreadyProcessed()` check (lines 75-77)
- **Keep** `transferWasAlreadyProcessed()` helper method (lines 115-120)

### 4. Update Documentation
- Update command handler example in `docs/architecture/DCB_AND_CRABLET.md`
- **Remove** the `.withIdempotencyCheck()` call from the example
- Show that manual checks handle idempotency, DCB handles concurrency

## Error Handling Flow

### Idempotency Violations (Manual Check)
1. **Manual check detects duplicate** → `CommandResult.emptyWithReason()`
2. **CommandExecutor** → `ExecutionResult.idempotent()`
3. **Controller** → `200 OK` (correct for idempotent operations)

### Concurrency Violations (DCB Check)
1. **DCB detects state change** → `ConcurrencyException`
2. **GlobalExceptionHandler** → `409 Conflict` (correct for concurrency violations)

## Benefits

1. **Correct Semantics** - Idempotency returns 200 OK, concurrency returns 409 Conflict
2. **Performance** - Remove redundant DCB idempotency checks
3. **Clarity** - Clear separation between idempotency and concurrency concerns
4. **Consistency** - All handlers follow the same pattern
5. **No Breaking Changes** - External behavior remains the same

## Testing

All existing tests will continue to pass:
- Idempotency tests verify duplicates return 200 OK
- Concurrency tests verify state changes return 409 Conflict
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

