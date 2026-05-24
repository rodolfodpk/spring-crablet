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

## The 4 DCB Patterns — Decision Tree

```
Is this creating a NEW entity that must be unique?
  YES → Pattern 1: Idempotent
  NO  → Is the operation commutative? (order doesn't affect outcome)
          YES → Does the entity need to exist / be active?
                  NO  → Pattern 2: Commutative (AppendCondition.empty())
                  YES → Pattern 2b: CommutativeGuarded (lifecycle guard only)
          NO  → Pattern 3: NonCommutative (cursor-based)
```

| Pattern | Operation examples | `CommandDecision` | Parallel-safe? |
|---------|-------------------|-----------------|---------------|
| **1. Idempotent** | OpenWallet, CreateAccount | `Idempotent` | ✅ |
| **2. Commutative** | Deposit, AddItem | `Commutative` | ✅ |
| **2b. CommutativeGuarded** | Deposit on open wallet, enroll in active course | `CommutativeGuarded` | ✅ (same-type ops don't conflict; only lifecycle change conflicts) |
| **3. NonCommutative** | Withdraw, Transfer, ChangeCap | `NonCommutative` | ❌ (DCB detects conflict, app retries) |

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

**Commutative idempotency (automation retry safety):** When an automation can re-deliver the same event, the emitted `Commutative` or `CommutativeGuarded` decision should carry a stable business key via `.idempotent(key, tagKey, tagValue)`. `CommandExecutorImpl` then calls `appendIdempotent` instead of `appendIf`, which is a no-op if the tagged event already exists. Use a stable business key (not a random ID) as the idempotency tag.

```java
return CommandDecision.commutative(events).idempotent(type(DepositMade.class), DEPOSIT_ID, command.depositId());
```

### Pattern 2b — CommutativeGuarded (commutative with lifecycle guard)

Use when the operation is order-independent among concurrent same-type operations, but the entity must exist or be active. Examples: deposit into an open wallet, enroll in an active course.

```java
// 1. Project lifecycle state + capture guard position
Query lifecycleQuery = QueryBuilder.create()
    .events(type(WalletOpened.class), type(WalletClosed.class))
    .tag(WALLET_ID, command.walletId())
    .build();

ProjectionResult<WalletLifecycleState> guard = eventStore.project(
    lifecycleQuery, Cursor.zero(), WalletLifecycleState.class, List.of(lifecycleProjector));

// 2. Precondition check
if (!guard.state().isOpen()) throw new WalletNotOpenException(command.walletId());

// 3. Build event
AppendEvent event = AppendEvent.builder(type(DepositMade.class))
    .tag(WALLET_ID, command.walletId())
    .data(DepositMade.of(...))
    .build();

// 4. CommutativeGuarded — concurrent deposits don't conflict each other,
//    but a WalletClosed after guardPosition will throw ConcurrencyException
return CommandDecision.CommutativeGuarded.withLifecycleGuard(event, lifecycleQuery, guard.cursor());
```

**Guard query design rule:** the lifecycle query must include ONLY lifecycle event types (e.g., `WalletOpened`, `WalletClosed`) — NOT the commutative event type (e.g., `DepositMade`). Including the appended type in the guard query throws `IllegalArgumentException` at runtime.

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
| Including appended event type in CommutativeGuarded lifecycle query | `IllegalArgumentException` at runtime | Lifecycle query must have ONLY lifecycle types (e.g., WalletOpened/WalletClosed), never the commutative type |
| Using `Commutative` when entity must be active | Operation succeeds on closed/deleted entity | Use `CommutativeGuarded` with lifecycle guard |
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
