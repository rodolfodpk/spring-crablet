# Plan: Event-triggered read-model automation reliability

**Status:** Implemented.

## Goal

Make **event-triggered, read-model-backed automations reliable and clearly supported for both pure Java users and AI/codegen users**.

End state:

```text
Event wakes automation.
Automation reads modeled state.
Automation emits a retry-safe command.
If automation fails, it retries safely.
Pure Java users can do this directly.
AI/codegen users get the same Java API shape generated from event-model.yaml.
```

`event-model.yaml` is tooling input for AI workflows, codegen, diagrams, and validation. It is **not** runtime configuration and must not be required for framework correctness.

## Architecture Position

Keep automations event-triggered. Do not add a view-row poller for this problem.

The read model is an input to the automation decision, not the automation cursor. Views, automations, and outbox are independent consumers of the event log with independent progress tables.

```text
events table
  -> view processor updates read model and advances view_progress
  -> automation processor sees matching event and calls decide()
  -> automation reads read model
  -> automation emits command or no-ops
  -> automation_progress advances only after successful handling
```

If view projection succeeds and automation handling fails, the view remains updated and the automation retries from its own progress. This is expected. Recovery depends on retry-safe command execution.

## Runtime Contract: Java First

The Java APIs are the source of truth:

- `AutomationHandler`: explicit event-triggered automation.
- `ViewBackedAutomationHandler`: declares `getReadViewNames()`; wake events are inferred from runtime `ViewSubscription` beans.
- `CommandDecision`: describes append consistency and duplicate behavior.
- `event-model.yaml`: optional codegen/modeling layer that should produce the same Java shape hand-written users would write.

## Important Design Correction: Idempotency Is Orthogonal

The current command API treats idempotency as one append strategy:

```text
commutative
non-commutative
idempotent
```

That is not expressive enough for retry-safe automations. Idempotency is partly orthogonal to consistency strategy.

Examples:

- `OpenWallet`: entity creation, duplicate should throw or return idempotent depending on domain policy.
- `DepositMoney`: commutative with a lifecycle guard, but should also be duplicate-safe by `deposit_id`.
- `WithdrawMoney`: non-commutative, but should also be duplicate-safe by `withdrawal_id`.
- `SendWelcomeNotification`: one notification per wallet, duplicate should return idempotent.

Target model:

```text
append consistency:
  - commutative
  - commutative with lifecycle guard
  - non-commutative

duplicate policy:
  - none
  - return idempotent
  - throw duplicate
```

The event store already has the lower-level shape for this through `AppendCondition`, which carries both `concurrencyQuery` and `idempotencyQuery`. The command-level API should expose that composition.

## Work Plan

Steps 1 and 2 are independent. Step 1 is additive validation around existing view-backed automation behavior. Step 2 is an additive command API extension that should preserve the existing command decision variants and backward compatibility.

### 1. Runtime Guardrails For View-backed Automations

`ViewBackedAutomationHandler` already documents that startup should fail when views are missing or have no declared event types. `AutomationDefinitionResolver` already implements part of that contract:

- missing `ViewSubscriptionLookup` fails.
- missing referenced view fails.
- referenced view with empty event types fails.

Verify that behavior with tests, and add the missing fail-fast validation:

- `ViewBackedAutomationHandler.getReadViewNames()` must be non-empty.
- The final inferred wake-event set must be non-empty after `getWakeEventsExtra()` and `getWakeEventsExclude()`.

This prevents accidental all-event matching, because an empty `EventSelection.getEventTypes()` means "match all event types".

### 2. Orthogonal Command Idempotency API

Keep the existing `IdempotentCommandHandler` and `CommandDecision.Idempotent` for backward compatibility and simple creation-style commands.

Add a composable idempotency option to command decisions, so commutative and non-commutative commands can carry duplicate protection.

Possible API direction:

```java
return CommandDecision.CommutativeGuarded
    .withLifecycleGuard(event, lifecycleQuery, streamPosition)
    .idempotent(type(DepositMade.class), DEPOSIT_ID, command.depositId());
```

```java
return CommandDecision.NonCommutative
    .of(event, decisionModel, streamPosition)
    .idempotent(type(WithdrawalMade.class), WITHDRAWAL_ID, command.withdrawalId());
```

Implementation approach:

- Add an `IdempotencyKey` value type: `eventType`, `tagKey`, `tagValue`, `OnDuplicate`.
- Add optional `IdempotencyKey` fields to the existing `CommandDecision.Commutative`, `CommandDecision.CommutativeGuarded`, and `CommandDecision.NonCommutative` records.
- Do not add new sealed variants for this work. Keeping the existing sealed variants avoids changing the `permits` list and keeps the existing `events()` switch shape.
- Add "wither" methods on those records. Each method returns a new record instance with the same consistency fields and a populated `IdempotencyKey`.
- Update existing static factories, such as `CommandDecision.CommutativeGuarded.withLifecycleGuard(...)` and `CommandDecision.NonCommutative.of(...)`, to pass `null` for the new optional idempotency field so existing call sites keep their current behavior.
- Update `CommandExecutorImpl` to build an `AppendCondition` with both concurrency and idempotency checks where needed.
- Preserve existing duplicate handling semantics: `RETURN_IDEMPOTENT` returns `ExecutionResult.idempotent(...)`; `THROW` raises a concurrency/duplicate exception.

Default duplicate policy:

- `idempotent(eventType, tagKey, tagValue)` defaults to `OnDuplicate.RETURN_IDEMPOTENT`, matching existing `CommandDecision.Idempotent.of(...)`.
- Overloads should allow `OnDuplicate.THROW` for creation/uniqueness flows where duplicates are domain errors.

Before:

```java
AppendEvent event = AppendEvent.builder(type(DepositMade.class))
    .tag(WALLET_ID, command.walletId())
    .tag(DEPOSIT_ID, command.depositId())
    .data(deposit)
    .build();

Query lifecycleGuard = WalletQueryPatterns.walletLifecycleModel(command.walletId());
return CommandDecision.CommutativeGuarded.withLifecycleGuard(
    event, lifecycleGuard, projection.streamPosition());
```

After:

```java
AppendEvent event = AppendEvent.builder(type(DepositMade.class))
    .tag(WALLET_ID, command.walletId())
    .tag(DEPOSIT_ID, command.depositId())
    .data(deposit)
    .build();

Query lifecycleGuard = WalletQueryPatterns.walletLifecycleModel(command.walletId());
return CommandDecision.CommutativeGuarded
    .withLifecycleGuard(event, lifecycleGuard, projection.streamPosition())
    .idempotent(type(DepositMade.class), DEPOSIT_ID, command.depositId());
```

Before:

```java
AppendEvent event = AppendEvent.builder(type(WithdrawalMade.class))
    .tag(WALLET_ID, command.walletId())
    .tag(WITHDRAWAL_ID, command.withdrawalId())
    .data(withdrawal)
    .build();

return CommandDecision.NonCommutative.of(
    event, decisionModel, projection.streamPosition());
```

After:

```java
AppendEvent event = AppendEvent.builder(type(WithdrawalMade.class))
    .tag(WALLET_ID, command.walletId())
    .tag(WITHDRAWAL_ID, command.withdrawalId())
    .data(withdrawal)
    .build();

return CommandDecision.NonCommutative
    .of(event, decisionModel, projection.streamPosition())
    .idempotent(type(WithdrawalMade.class), WITHDRAWAL_ID, command.withdrawalId());
```

Record/constructor impact:

- `CommandDecision.CommutativeGuarded` already has a compact constructor that validates the lifecycle guard does not include appended event types. Preserve that validation when adding the optional idempotency key.
- Keep the validation scoped to the lifecycle guard. The idempotency event type is expected to match the appended event type in common flows and must not trigger the lifecycle-overlap validation.

Validation:

- `eventType`, `tagKey`, and `tagValue` must be non-blank. Enforce this in the `IdempotencyKey` record compact constructor so the invariant is centralized regardless of which command decision carries the key.
- Docs must state that idempotency keys are stable business keys stored as event tags, not transaction IDs or random event identities.

### 3. Failure Semantics Documentation

Document this runtime behavior in framework docs:

```text
Views, automations, and outbox have independent progress.
A view can successfully project an event while an automation fails on that event.
Automation progress advances only after decide() and all decisions succeed.
Automation commands must be duplicate-safe because retries can happen after partial success.
```

Also document the known retry hazard:

```text
Automation executes command successfully.
Process crashes before automation_progress advances.
Automation retries the same event.
The emitted command must detect the duplicate and return idempotent or throw according to policy.
```

### 4. Pure Java Documentation

Update `crablet-automations/README.md` and `crablet-commands/README.md` with Java-first examples:

- plain `AutomationHandler`
- `ViewBackedAutomationHandler`
- automation emitting a duplicate-safe command
- commutative command with an idempotency key
- non-commutative command with an idempotency key
- simple creation-style command using existing `IdempotentCommandHandler`

The docs should make clear that Java users do not need `event-model.yaml`.

### 5. Codegen And event-model.yaml Alignment

Update `docs/user/ai-tooling/EVENT_MODEL_FORMAT.md` to say:

- `event-model.yaml` is optional tooling input, not runtime config.
- `readsViews` is preferred over singular `readsView`.
- `readsView` remains valid legacy one-view sugar indefinitely for backward compatibility. New examples and generated output should prefer `readsViews`. If validation tooling supports warnings, emit a non-blocking deprecation-style warning for `readsView`; do not fail existing models.
- `wakeEventsExtra` and `wakeEventsExclude` are codegen/model metadata for view-backed automations.
- generated idempotent or retry-safe commands must declare a stable duplicate key.

Possible YAML shape:

```yaml
commands:
  - name: DepositMoney
    pattern: commutative
    produces: [DepositMade]
    guardEvents: [WalletOpened, WalletClosed]
    idempotency:
      event: DepositMade
      tag: deposit_id
      valueFrom: depositId
      onDuplicate: return-idempotent

  - name: SendWelcomeNotification
    pattern: idempotent
    produces: [WelcomeNotificationSent]
    idempotency:
      event: WelcomeNotificationSent
      tag: wallet_id
      valueFrom: walletId
      onDuplicate: return-idempotent

automations:
  - name: WelcomeNotificationAutomation
    readsViews: [PendingWelcomeNotifications]
    emitsCommand: SendWelcomeNotification
```

Generation rules:

- `readsViews` present: generate an interface extending `ViewBackedAutomationHandler`.
- no `readsViews`: generate an interface extending `AutomationHandler`.
- never generate `getEventTypes()` for `ViewBackedAutomationHandler`.
- command handler sketches must show duplicate-safe command decisions when `idempotency` metadata exists.
- `pattern: idempotent` plus `idempotency:` should generate/sketch the existing `CommandDecision.Idempotent.of(...)` style using the declared key. `pattern: commutative` or `pattern: non-commutative` plus `idempotency:` should generate/sketch the new wither-based `.idempotent(...)` style on the relevant command decision.
- missing duplicate-key metadata for commands emitted by retrying automations should produce a blocking diagnostic or explicit unresolved TODO, not silently generate unsafe guidance.

### 6. Tests

Runtime tests:

- view-backed automation with empty read views fails.
- view-backed automation whose exclusions remove all inferred wake events fails.
- valid view-backed automation resolves expected wake events.
- duplicate-safe commutative command returns `ExecutionResult.idempotent(...)` on retry.
- duplicate-safe non-commutative command returns `ExecutionResult.idempotent(...)` on retry.
- automation retry after command success does not append a duplicate event.
- crash-recovery simulation: command execution succeeds, automation progress is intentionally left unadvanced, the same event is handled again, and duplicate protection prevents a second event append.

Codegen/model tests:

- `readsViews` generates `ViewBackedAutomationHandler`.
- plain `triggeredBy` generates `AutomationHandler`.
- idempotency metadata appears in generated command handler sketches.
- missing idempotency metadata for retry-sensitive generated flows is caught.

### 7. Examples

Add or update examples in two tracks:

- Pure Java example: use `examples/wallet-example-app` plus `shared-examples-domain` for a real runtime example of view-backed automation plus duplicate-safe command, no `event-model.yaml` required.
- AI/codegen example: update `docs/user/examples/*event-model.yaml` and the relevant generated/docs sample path to show `readsViews` and command `idempotency` metadata producing the same Java API shape.
- README snippets: include concise versions in `crablet-automations/README.md` and `crablet-commands/README.md`, but keep the runnable behavior in the example app/tests.

The examples should demonstrate that retry-safety comes from stable business idempotency tags and duplicate policies, not random transaction IDs.

## Non-goals

- Do not introduce a view-row poller for this work.
- Do not make `event-model.yaml` runtime configuration.
- Do not require generated apps to use APIs that hand-written Java apps cannot use directly.
- Do not require projection barriers in this pass. Document eventual consistency first; design barriers only if concrete workflows need them.
