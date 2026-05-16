---
name: crablet-dcb
description: >
  Use this skill when the user wants to:
    - Understand the DCB (Dynamic Consistency Boundary) pattern
    - Choose the right AppendCondition for a command handler
    - Diagnose ConcurrencyException or idempotency issues
    - Design consistency boundaries for a new feature
    - Map DCB concepts (decision model, cursor, criteria) to spring-crablet code
argument-hint: [optional: command or scenario to analyze]
---

# Crablet DCB (Dynamic Consistency Boundary) Skill

DCB was introduced by Sara Pellegrini ("Killing the Aggregate"). Official spec: https://dcb.events/

## Core Idea in One Sentence

Instead of locking a fixed aggregate stream, you define a **dynamic query (decision model)** that captures which events are relevant to your business decision — then check if any of those events appeared after you read state (cursor-based optimistic locking).

## The 3 DCB Patterns — Decision Tree

```
Is this creating a NEW entity that must be unique?
  YES → Pattern 1: withIdempotencyCheck()
  NO  → Is the operation commutative? (order doesn't affect outcome)
          YES → Pattern 2: AppendCondition.empty()
          NO  → Pattern 3: AppendConditionBuilder(decisionModel, cursor)
```

| Pattern | Operation examples | AppendCondition | Parallel-safe? |
|---------|-------------------|-----------------|---------------|
| **1. Idempotency** | OpenWallet, CreateAccount | `withIdempotencyCheck(type, tagKey, tagValue)` | ✅ |
| **2. Empty (commutative)** | Deposit, AddItem | `AppendCondition.empty()` | ✅ |
| **3. Cursor (non-commutative)** | Withdraw, Transfer, ChangeCap | `new AppendConditionBuilder(decisionModel, cursor).build()` | ❌ (DCB detects conflict, app retries) |

## How Each Pattern Works

### Pattern 1 — Idempotency Check (entity creation)

```java
AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
    .withIdempotencyCheck(type(WalletOpened.class), WALLET_ID, command.walletId())
    .build();
```

- Searches ALL events (ignores cursor) for an event with that tag
- Found → return success (already done, idempotent)
- Not found → insert event
- Uses PostgreSQL advisory locks (needed because no prior cursor exists)
- ~4x slower than cursor-based but necessary for uniqueness

### Pattern 2 — Empty Condition (commutative)

```java
AppendCondition condition = AppendCondition.empty();
```

- No conflict detection at all
- Safe when `op(A) then op(B)` = `op(B) then op(A)` in any observable way
- Deposits: +$10 then +$20 = +$20 then +$10 ✓
- Maximum throughput

### Pattern 3 — Cursor-Based Check (non-commutative)

```java
// 1. Project state AND capture cursor
Query decisionModel = QueryBuilder.create()
    .events(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class))
    .tag(WALLET_ID, command.walletId())
    .build();

ProjectionResult<WalletBalanceState> projection = eventStore.project(
    decisionModel, Cursor.zero(), WalletBalanceState.class, List.of(projector));

// 2. Business logic using projected state
if (!projection.state().hasSufficientFunds(command.amount())) {
    throw new InsufficientFundsException(...);
}

// 3. Condition uses SAME decision model + cursor
AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
    .build();
```

**What the check does:** "Did any event matching `decisionModel` appear after cursor position X?"
- Yes → `ConcurrencyException` → application layer retries (re-read, re-validate, re-append)
- No → append succeeds

## Decision Model = Consistency Boundary

The decision model query defines exactly which events can invalidate your business decision. Smaller = less contention.

```
wallet_id=alice events only → Operations on alice don't block operations on bob
from_wallet_id=alice OR to_wallet_id=alice → Transfer reads both wallets atomically
```

Multi-entity consistency without aggregates or sagas:
```java
// Transfer: one event, two tags, two wallets in one boundary
AppendEvent transfer = AppendEvent.builder(type(MoneyTransferred.class))
    .tag(FROM_WALLET_ID, fromId)   // part of fromWallet's boundary
    .tag(TO_WALLET_ID, toId)       // part of toWallet's boundary
    .data(event)
    .build();
```

## Common Mistakes

| Mistake | Symptom | Fix |
|---------|---------|-----|
| Using `empty()` for Withdraw | Overdrafts possible | Use cursor-based (Pattern 3) |
| Using cursor for Deposit | Unnecessary conflicts under load | Use `empty()` (Pattern 2) |
| Using cursor for entity creation | No prior cursor, wrong semantics | Use `withIdempotencyCheck()` (Pattern 1) |
| Decision model tags don't match event tags | False conflicts or missed conflicts | Tags must exactly match what's on the events |
| Retrying inside handler | Handler runs inside a transaction | Retry at the application/controller layer |

## Diagnosing ConcurrencyException

```
AppendCondition violated: duplicate operation detected
  Hint: Check that your idempotency tag uniquely identifies the operation
```

1. Read the `Hint:` in the message — idempotency vs concurrency
2. Check `matchingEventsCount` (how many conflicting events exist)
3. Enable debug: `logging.level.com.crablet.eventstore=DEBUG` to see exact DCB check params
4. Verify decision model tags exactly match tags on stored events

## Mapping DCB Vocabulary to spring-crablet

| DCB spec term | spring-crablet construct |
|---------------|--------------------------|
| **Criteria / Decision Model** | `Query` built with `QueryBuilder` |
| **Cursor / Position** | `Cursor` from `ProjectionResult.cursor()` |
| **stateChanged check** | `new AppendConditionBuilder(decisionModel, cursor).build()` |
| **alreadyExists check** | `.withIdempotencyCheck(type, tagKey, tagValue)` |
| **Append** | `eventStore.appendIf(events, condition)` (called by `CommandExecutor`) |
| **ConcurrencyException** | `ConcurrencyException` — app layer should retry |

## Complete Command Handler Reference

```java
@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {

    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // 1. Decision model = consistency boundary
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());

        // 2. Project state + capture cursor
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
            decisionModel, Cursor.zero(), WalletBalanceState.class, List.of(new WalletBalanceStateProjector()));

        // 3. Business rules on projected state
        if (!projection.state().isExisting()) throw new WalletNotFoundException(command.walletId());
        if (!projection.state().hasSufficientFunds(command.amount()))
            throw new InsufficientFundsException(command.walletId(), projection.state().balance(), command.amount());

        // 4. Build event
        AppendEvent event = AppendEvent.builder(type(WithdrawalMade.class))
            .tag(WALLET_ID, command.walletId())
            .tag(WITHDRAWAL_ID, command.withdrawalId())
            .data(WithdrawalMade.of(...))
            .build();

        // 5. Cursor-based condition (non-commutative)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor()).build();

        return CommandResult.of(List.of(event), condition);
        // CommandExecutor calls appendIf() automatically — do NOT call it here
    }
}
```

## When Invoked With Arguments (`$ARGUMENTS`)

If the user provides a command or scenario to analyze:

1. **Identify the pattern** — entity creation? commutative? non-commutative?
2. **Define the decision model** — which event types + which tags scope the boundary?
3. **Show the AppendCondition** — which pattern applies and why
4. **Note retry behavior** — where should ConcurrencyException be caught?
5. **Optionally scaffold** the full handler if the user confirms

Keep it practical. Lead with the pattern decision, then the code.

## Cross-Links

- **Automation retries and duplicate work**: at-least-once automation delivery can re-run a command.
  Make the downstream command idempotent (Pattern 1) to protect against duplicates. See the
  **Process Rule** in `crablet-app-dev` for the full 6-step pattern.
- **Cross-entity operations with external publication**: the DCB boundary protects consistency
  inside the eventstore. Reliable external publication belongs in `crablet-outbox`, not in command
  handlers or automation handlers. Do not call external systems from inside a command handler.
- **Tags and decision models**: tags define the DCB boundary. Tags on the decision model must
  exactly match the tags on the appended events, or the cursor check misses the conflict.
