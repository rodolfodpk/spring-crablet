# Command Patterns Guide

## Overview

This guide explains when to use DCB cursor checks and when operations can run without them. Understanding the difference between **commutative** and **non-commutative** operations is key to proper DCB implementation.

## Operation Types

### Commutative Operations
**Definition:** Operations where order doesn't matter - final result is the same regardless of execution order.

**Examples:**
- Adding money to a wallet (+$10 then +$20 = +$20 then +$10)
- Opening a wallet (idempotent - same result every time)
- Reading state (doesn't change state)

**DCB Check:** ❌ Not required - use `AppendCondition.empty()`

### Non-Commutative Operations  
**Definition:** Operations where order matters - final result depends on execution order.

**Examples:**
- Withdrawing money (balance affects whether operation succeeds)
- Transferring money (both wallet balances affect success)
- Any operation that depends on current state values

**DCB Check:** ✅ Required - use `AppendConditionBuilder(decisionModel, cursor)`

## Pattern 1: Entity Creation with Idempotency

### OpenWallet Command

**Why idempotency check needed:** Wallet creation requires uniqueness - no wallet should be created twice. Uses idempotency check (not cursor) because there's no prior state to read.

```java
@Component
public class OpenWalletCommandHandler implements CommandHandler<OpenWalletCommand> {
    
    @Override
    public CommandResult handle(EventStore eventStore, OpenWalletCommand command) {
        // Command is already validated at construction with YAVI
        
        // Create event (optimistic - assume wallet doesn't exist)
        WalletOpened walletOpened = WalletOpened.of(
                command.walletId(),
                command.owner(),
                command.initialBalance()
        );
        
        AppendEvent event = AppendEvent.builder(WALLET_OPENED)
                .tag(WALLET_ID, command.walletId())
                .data(walletOpened)
                .build();
        
        // Build condition with idempotency check using DCB pattern
        // Fails if ANY WalletOpened event exists for this wallet_id
        // No concurrency check needed - only idempotency matters
        AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
                .withIdempotencyCheck(WALLET_OPENED, WALLET_ID, command.walletId())
                .build();
        
        return CommandResult.of(List.of(event), condition);
    }
}
```

**Key Points:**
- ✅ Idempotent: can run multiple times safely
- ✅ Uses `withIdempotencyCheck()`: enforces uniqueness atomically
- ✅ No cursor check: not needed for wallet creation (no prior state)
- ✅ Optimistic: creates event first, checks atomically via `appendIf`

## Pattern 2: Commutative Operations (No Cursor Check)

### Deposit Command

**Why no cursor needed:** Deposits are commutative - order doesn't affect final balance.

```java
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    
    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // Project to validate wallet exists and get current balance
        WalletBalanceState state = balanceProjector.projectWalletBalance(eventStore, command.walletId()).state();
        
        if (!state.isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }
        
        int newBalance = state.balance() + command.amount();
        
        DepositMade deposit = DepositMade.of(
                command.depositId(),
                command.walletId(),
                command.amount(),
                newBalance,
                command.description()
        );
        
        AppendEvent event = AppendEvent.builder(DEPOSIT_MADE)
                .tag(WALLET_ID, command.walletId())
                .tag(DEPOSIT_ID, command.depositId())
                .data(deposit)
                .build();
        
        // Deposits are commutative - order doesn't matter
        // Balance: $100 → +$10 → +$20 = $130 (same as +$20 → +$10)
        // No DCB cursor check needed - allows parallel deposits
        AppendCondition condition = AppendCondition.empty();
        
        return CommandResult.of(List.of(event), condition);
    }
}
```

**Key Points:**
- ✅ Commutative: +$10 then +$20 = +$20 then +$10
- ✅ No cursor check: parallel deposits don't conflict
- ✅ Idempotency via `deposit_id` tag

## Pattern 3: Non-Commutative Operations (Cursor Check Required)

### Withdraw Command

**Why cursor needed:** Withdrawals depend on current balance. Concurrent withdrawals can cause overdrafts without DCB.

**Problem without DCB:**
```
Initial balance: $100

Thread 1: Reads balance=$100, wants to withdraw $80
Thread 2: Reads balance=$100, wants to withdraw $80

Both threads see same balance ($100), both succeed → Final balance: -$60 ❌
```

**Solution with DCB:**
```java
@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {
    
    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // Use decision model query
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        
        // Project state with cursor
        ProjectionResult<WalletBalanceState> projection = 
                balanceProjector.projectWalletBalance(eventStore, command.walletId(), decisionModel);
        WalletBalanceState state = projection.state();
        
        if (!state.isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }
        
        int newBalance = state.balance() - command.amount();
        
        if (newBalance < 0) {
            throw new InsufficientFundsException(command.walletId(), state.balance(), command.amount());
        }
        
        WithdrawalMade withdrawal = WithdrawalMade.of(
                command.walletId(),
                command.withdrawalId(),
                command.amount(),
                newBalance,
                command.description()
        );
        
        AppendEvent event = AppendEvent.builder(WITHDRAWAL_MADE)
                .tag(WALLET_ID, command.walletId())
                .tag(WITHDRAWAL_ID, command.withdrawalId())
                .data(withdrawal)
                .build();
        
        // Withdrawals are non-commutative - order matters for balance validation
        // DCB cursor check REQUIRED: prevents concurrent withdrawals exceeding balance
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
                .build();
        
        return CommandResult.of(List.of(event), condition);
    }
}
```

**Key Points:**
- ❌ Non-commutative: order affects whether operation succeeds
- ✅ Cursor check: detects if balance changed since projection
- ❌ Cannot run in parallel on same wallet (DCB detects conflict, application must retry)

### Transfer Command

**Why cursor needed:** Transfers affect two wallets. Order matters for both balances.

**Problem without DCB:**
```
Wallet A: $100, Wallet B: $50

Thread 1: Transfer $80 from A to B (sees A=$100 ✅)
Thread 2: Transfer $60 from A to B (sees A=$100 ✅)

Both succeed → Wallet A: -$40 ❌
```

**Solution with DCB:**
```java
@Component
public class TransferMoneyCommandHandler implements CommandHandler<TransferMoneyCommand> {
    
    @Override
    public CommandResult handle(EventStore eventStore, TransferMoneyCommand command) {
        // Project both wallet balances with cursor
        TransferProjectionResult transferProjection = projectTransferState(eventStore, command);
        TransferState state = transferProjection.state();
        
        // Validate both wallets exist
        if (!state.fromWallet().isExisting()) {
            throw new WalletNotFoundException(command.fromWalletId());
        }
        if (!state.toWallet().isExisting()) {
            throw new WalletNotFoundException(command.toWalletId());
        }
        
        // Validate sufficient funds
        int newFromBalance = state.fromWallet().balance() - command.amount();
        if (newFromBalance < 0) {
            throw new InsufficientFundsException(command.fromWalletId(), 
                    state.fromWallet().balance(), command.amount());
        }
        
        int newToBalance = state.toWallet().balance() + command.amount();
        
        // Create transfer event
        MoneyTransferred transfer = MoneyTransferred.of(
                command.transferId(),
                command.fromWalletId(),
                command.toWalletId(),
                command.amount(),
                newFromBalance,
                newToBalance,
                command.description()
        );
        
        AppendEvent event = AppendEvent.builder(MONEY_TRANSFERRED)
                .tag(TRANSFER_ID, command.transferId())
                .tag(FROM_WALLET_ID, command.fromWalletId())
                .tag(TO_WALLET_ID, command.toWalletId())
                .data(transfer)
                .build();
        
        // Transfers are non-commutative - order matters for both wallet balances
        // DCB cursor check REQUIRED: prevents concurrent transfers causing overdrafts
        AppendCondition condition = new AppendConditionBuilder(
                transferProjection.decisionModel(), 
                transferProjection.cursor()
        ).build();
        
        return CommandResult.of(List.of(event), condition);
    }
    
    private TransferProjectionResult projectTransferState(EventStore store, TransferMoneyCommand cmd) {
        Query decisionModel = WalletQueryPatterns.transferDecisionModel(
                cmd.fromWalletId(),
                cmd.toWalletId()
        );
        
        // Create projector instance per projection (immutable, thread-safe)
        TransferStateProjector projector = new TransferStateProjector(cmd.fromWalletId(), cmd.toWalletId());
        
        ProjectionResult<TransferState> result = store.project(
                decisionModel, 
                Cursor.zero(), 
                TransferState.class, 
                List.of(projector)
        );
        
        return new TransferProjectionResult(result.state(), result.cursor(), decisionModel);
    }
}
```

**Key Points:**
- ❌ Non-commutative: order matters for both wallets
- ✅ Cursor check: prevents concurrent overdrafts
- ❌ Cannot run in parallel on same wallets

## Summary Table

| Operation | Type | Needs Cursor? | Uses Idempotency Check? | Can Run Parallel? | Idempotency |
|-----------|------|---------------|-------------------------|-------------------|-------------|
| **OpenWallet** | Idempotent | ❌ | ✅ | ✅ | wallet_id tag |
| **Deposit** | Commutative | ❌ | ❌ | ✅ | deposit_id tag |
| **Withdraw** | Non-commutative | ✅ | ❌ | ❌ | withdrawal_id tag |
| **Transfer** | Non-commutative | ✅ | ❌ | ❌ | transfer_id tag |

## When to Use Each Pattern

### Use `withIdempotencyCheck()` When:
- ✅ Creating new entities with uniqueness requirements (OpenWallet)
- ✅ No prior state exists to read cursor from
- ✅ Need to prevent duplicates atomically
- ✅ Want advisory locks for uniqueness

### Use `AppendCondition.empty()` When:
- ✅ Operation is **commutative** (Deposit)
- ✅ Result doesn't depend on state values
- ✅ Want maximum parallel throughput

### Use `AppendConditionBuilder(decisionModel, cursor)` When:
- ✅ Operation **order matters** (Withdraw, Transfer)
- ✅ Result **depends on current state** (balance checks)
- ✅ Need to prevent **race conditions** on same resource
- ✅ Want **optimistic concurrency control**

## Common Mistakes

❌ **Not using idempotency check for wallet creation:**
```java
// WRONG: No uniqueness check, allows duplicates
AppendCondition condition = AppendCondition.empty();
```

✅ **Correct:**
```java
// RIGHT: Wallet creation requires idempotency check
AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
        .withIdempotencyCheck(WALLET_OPENED, WALLET_ID, walletId)
        .build();
```

❌ **Using cursor for deposits:**
```java
// WRONG: Deposits don't need cursor check
AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor()).build();
```

✅ **Correct:**
```java
// RIGHT: Deposits are commutative, no cursor needed
AppendCondition condition = AppendCondition.empty();
```

❌ **Not using cursor for withdrawals:**
```java
// WRONG: Withdrawals need cursor to prevent overdrafts
AppendCondition condition = AppendCondition.empty();
```

✅ **Correct:**
```java
// RIGHT: Withdrawals are non-commutative, cursor required
AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor()).build();
```

## Learn More

- [Getting Started Guide](GETTING_STARTED.md) - Complete setup walkthrough
- [DCB Documentation](DCB_AND_CRABLET.md) - Deep dive into DCB
- [Testing Guide](TESTING.md) - How to test command handlers

