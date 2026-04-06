# Command Patterns Guide

## Quick Reference

**Pattern Decision Tree:**
1. **Creating new entity?** → Use `IdempotentCommandHandler` / `appendIdempotent` (Pattern 1)
2. **Commutative operation?** (order doesn't matter) → Use `CommutativeCommandHandler` / `appendCommutative` (Pattern 2)
3. **Non-commutative operation?** (order matters) → Use `NonCommutativeCommandHandler` / `appendNonCommutative` (Pattern 3)

**Summary:**

| Operation | Type | Handler Interface | Can Run Parallel? |
|-----------|------|------------------|-------------------|
| **OpenWallet** | Entity Creation | `IdempotentCommandHandler` | ✅ |
| **Deposit** | Commutative | `CommutativeCommandHandler` | ✅ |
| **Withdraw** | Non-Commutative | `NonCommutativeCommandHandler` | ❌ |
| **Transfer** | Non-Commutative | `NonCommutativeCommandHandler` | ❌ |

📖 **Details:** See [patterns below](#patterns) and [when to use each](#when-to-use-each-pattern).

## Overview

This guide explains when to use DCB streamPosition checks and when operations can run without them. Understanding the difference between **commutative** and **non-commutative** operations is key to proper DCB implementation.

**Note:** Command handlers return a `CommandDecision` (via the sub-interface's `decide()` method). The `CommandExecutor` automatically calls the appropriate append method (`appendCommutative`, `appendNonCommutative`, or `appendIdempotent`) based on the decision type.

## Command Handler Registration

Command handlers are automatically discovered by Spring:

1. **Command Interface**: Commands implement an interface annotated with `@JsonSubTypes`:
   ```java
   @JsonSubTypes({
       @JsonSubTypes.Type(value = DepositCommand.class, name = "deposit"),
       @JsonSubTypes.Type(value = WithdrawCommand.class, name = "withdraw")
   })
   public interface WalletCommand { }
   ```

2. **Handler Implementation**: Handlers implement one of the three sub-interfaces:
   ```java
   @Component
   public class DepositCommandHandler implements CommutativeCommandHandler<DepositCommand> {
       @Override
       public CommandDecision.Commutative decide(EventStore eventStore, DepositCommand command) {
           // Implementation...
       }
   }
   ```

3. **Automatic Registration**: Command type is extracted from handler's generic type parameter using reflection. Handlers are registered at startup.

## Operation Types

### Commutative Operations
Operations where order doesn't affect the final result (e.g., deposits: +$10 then +$20 = +$20 then +$10).

**DCB Check:** ❌ Not required — use `CommutativeCommandHandler` / `appendCommutative`

### Non-Commutative Operations
Operations where order matters — final result depends on execution order (e.g., withdrawals depend on current balance).

**DCB Check:** ✅ Required — use `NonCommutativeCommandHandler` / `appendNonCommutative`

## Patterns

### Pattern 1: Entity Creation with Idempotency

**Use case:** Creating new entities with uniqueness requirements (e.g., OpenWallet).

**Why idempotency check needed:** No prior state exists, so streamPosition check isn't possible. Advisory locks prevent duplicate creation.

```java
@Component
public class OpenWalletCommandHandler implements IdempotentCommandHandler<OpenWalletCommand> {
    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, OpenWalletCommand command) {
        WalletOpened walletOpened = WalletOpened.of(
            command.walletId(), command.owner(), command.initialBalance()
        );
        
        AppendEvent event = AppendEvent.builder(type(WalletOpened.class))
            .tag(WALLET_ID, command.walletId())
            .data(walletOpened)
            .build();
        
        // Idempotency check prevents duplicate wallet creation
        return CommandDecision.Idempotent.of(event, type(WalletOpened.class), WALLET_ID, command.walletId());
    }
}
```

**Key Points:**
- ✅ Uses `IdempotentCommandHandler` for uniqueness
- ✅ No streamPosition check (no prior state)
- ✅ Idempotent: can run multiple times safely

### Pattern 2: Commutative Operations

**Use case:** Operations where order doesn't matter (e.g., Deposit).

**Why no streamPosition needed:** Commutative operations don't conflict — parallel operations produce same result regardless of order.

```java
@Component
public class DepositCommandHandler implements CommutativeCommandHandler<DepositCommand> {
    @Override
    public CommandDecision.Commutative decide(EventStore eventStore, DepositCommand command) {
        // Project to validate wallet exists
        Query query = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
            query, StreamPosition.zero(), WalletBalanceState.class, List.of(projector));
        
        if (!projection.state().isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }
        
        int newBalance = projection.state().balance() + command.amount();
        DepositMade deposit = DepositMade.of(
            command.depositId(), command.walletId(), command.amount(), 
            newBalance, command.description()
        );
        
        AppendEvent event = AppendEvent.builder(type(DepositMade.class))
            .tag(WALLET_ID, command.walletId())
            .tag(DEPOSIT_ID, command.depositId())  // Optional: for application-level idempotency
            .data(deposit)
            .build();
        
        // Commutative: order doesn't affect final result
        return CommandDecision.Commutative.of(event);
    }
}
```

**Key Points:**
- ✅ Commutative: +$10 then +$20 = +$20 then +$10
- ✅ No streamPosition check: parallel deposits don't conflict
- ✅ Optional `deposit_id` tag for application-level idempotency

### Pattern 3: Non-Commutative Operations

**Use case:** Operations where order matters (e.g., Withdraw, Transfer).

**Why streamPosition needed:** Prevents race conditions. Concurrent operations on same resource must be serialized.

#### Withdraw Example

```java
@Component
public class WithdrawCommandHandler implements NonCommutativeCommandHandler<WithdrawCommand> {
    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, WithdrawCommand command) {
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
            decisionModel, StreamPosition.zero(), WalletBalanceState.class, List.of(projector));
        
        WalletBalanceState state = projection.state();
        if (!state.isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }
        
        int newBalance = state.balance() - command.amount();
        if (newBalance < 0) {
            throw new InsufficientFundsException(command.walletId(), state.balance(), command.amount());
        }
        
        WithdrawalMade withdrawal = WithdrawalMade.of(
            command.walletId(), command.withdrawalId(), command.amount(), 
            newBalance, command.description()
        );
        
        AppendEvent event = AppendEvent.builder(WITHDRAWAL_MADE)
            .tag(WALLET_ID, command.walletId())
            .tag(WITHDRAWAL_ID, command.withdrawalId())
            .data(withdrawal)
            .build();
        
        // StreamPosition check prevents concurrent withdrawals exceeding balance
        return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
    }
}
```

#### Transfer Example

Transfers affect two wallets and require a streamPosition check spanning both:

```java
@Component
public class TransferMoneyCommandHandler implements NonCommutativeCommandHandler<TransferMoneyCommand> {
    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, TransferMoneyCommand command) {
        // Project both wallet balances with a combined decision model
        Query decisionModel = WalletQueryPatterns.transferDecisionModel(
            command.fromWalletId(), command.toWalletId());
        TransferStateProjector projector = new TransferStateProjector(
            command.fromWalletId(), command.toWalletId());
        ProjectionResult<TransferState> projection = eventStore.project(
            decisionModel, StreamPosition.zero(), TransferState.class, List.of(projector));
        
        TransferState state = projection.state();
        // Validate wallets exist and sufficient funds...
        
        MoneyTransferred transfer = MoneyTransferred.of(/* ... */);
        AppendEvent event = AppendEvent.builder(MONEY_TRANSFERRED)
            .tag(FROM_WALLET_ID, command.fromWalletId())
            .tag(TO_WALLET_ID, command.toWalletId())
            .data(transfer)
            .build();
        
        // StreamPosition check prevents concurrent transfers causing overdrafts
        return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
    }
}
```

**Key Points:**
- ❌ Non-commutative: order affects whether operation succeeds
- ✅ StreamPosition check: detects if state changed since projection
- ❌ Cannot run in parallel on same resource (DCB detects conflict, application retries)

**For more complex multi-entity examples**, see Course Subscriptions (`SubscribeStudentToCourseCommandHandler`) which demonstrates capacity limits, subscription limits, and duplicate checks.

## When to Use Each Pattern

### Use `IdempotentCommandHandler` When:
- ✅ Creating new entities with uniqueness requirements
- ✅ No prior state exists to read stream position from
- ✅ Need to prevent duplicates atomically

**Why Advisory Locks Are Required:**

Idempotency checks use PostgreSQL advisory locks to prevent race conditions when checking for duplicate entities. Unlike streamPosition-based checks, idempotency checks cannot rely on snapshot isolation because there's no prior state (stream position) to check against.

**Performance:** ~4x slower than streamPosition-based checks (due to advisory locks), but necessary for uniqueness.

### Use `CommutativeCommandHandler` When:
- ✅ Operation is **commutative** (order doesn't affect final result)
- ✅ Want maximum parallel throughput

### Use `NonCommutativeCommandHandler` When:
- ✅ Operation **order matters** (Withdraw, Transfer)
- ✅ Result **depends on current state** (balance checks)
- ✅ Need to prevent **race conditions** on same resource
- ✅ Want **optimistic concurrency control**

## Optional Operation ID Tags

Operation IDs like `deposit_id`, `withdrawal_id`, and `transfer_id` are **optional** tags for application-level idempotency (detecting duplicate operations if commands are retried).

**When to include:**
- ✅ When your application retries commands after failures
- ✅ When commands come from external systems that might retry

**When not needed:**
- When operations are commutative by design
- When you rely on DCB streamPosition checks for concurrency control

**Note:** These are different from DCB's `appendIdempotent`, which is an atomic database-level check for entity uniqueness.

## Common Mistakes

❌ **Not using idempotency check for wallet creation:**
```java
// WRONG: Allows duplicates — use IdempotentCommandHandler instead
return CommandDecision.Commutative.of(event);  // CommutativeCommandHandler — no duplicate guard
```

✅ **Correct:**
```java
// RIGHT: IdempotentCommandHandler with the uniqueness tag
return CommandDecision.Idempotent.of(event, type(WalletOpened.class), WALLET_ID, walletId);
```

❌ **Using streamPosition for deposits:**
```java
// WRONG: Deposits don't need streamPosition check — use CommutativeCommandHandler
return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
```

✅ **Correct:**
```java
// RIGHT: Deposits are commutative
return CommandDecision.Commutative.of(event);
```

❌ **Not using streamPosition for withdrawals:**
```java
// WRONG: Withdrawals need streamPosition to prevent overdrafts — use NonCommutativeCommandHandler
return CommandDecision.Commutative.of(event);
```

✅ **Correct:**
```java
// RIGHT: Withdrawals are non-commutative — use NonCommutativeCommandHandler
return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
```

## Learn More

- [Getting Started Guide](GETTING_STARTED.md) - Complete setup walkthrough
- [DCB Documentation](DCB_AND_CRABLET.md) - Deep dive into DCB
- [Testing Guide](TESTING.md) - How to test command handlers
