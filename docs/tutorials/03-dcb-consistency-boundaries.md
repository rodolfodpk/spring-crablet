# Part 3: DCB Consistency Boundaries

This tutorial stays in `crablet-eventstore` and introduces the core DCB idea.

Canonical compile fixture:
[docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial03DcbSample.java](../../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial03DcbSample.java)

## Why This Part Exists

Part 1 showed how to append and project events. Part 2 showed how to wrap writes in commands.

This part explains why Crablet needs more than a single aggregate stream for some business decisions.

Skip this part if your main interest is read models or side effects and you are comfortable with optimistic concurrency already.

You will learn:

- why a fixed aggregate stream is not enough for some invariants
- how to build a decision model with a query
- how `StreamPosition` protects state-dependent writes

Assume this import in the snippets below:

```java
import static com.crablet.eventstore.EventType.type;
```

## Example Constraint

A wallet must never go negative.

Two concurrent withdrawals can both observe the same balance and both decide “there is enough money” unless the write is tied to the exact state that was read.

## Decision Model

```java
public record WalletBalanceState(int balance, boolean exists) {}

public class WalletBalanceStateProjector implements StateProjector<WalletBalanceState> {

    @Override
    public List<String> getEventTypes() {
        return List.of(
            type(WalletOpened.class),
            type(DepositMade.class),
            type(WithdrawalMade.class)
        );
    }

    @Override
    public WalletBalanceState getInitialState() {
        return new WalletBalanceState(0, false);
    }

    @Override
    public WalletBalanceState transition(
            WalletBalanceState state,
            StoredEvent event,
            EventDeserializer deserializer) {
        WalletEvent walletEvent = deserializer.deserialize(event, WalletEvent.class);
        return switch (walletEvent) {
            case WalletOpened opened -> new WalletBalanceState(opened.initialBalance(), true);
            case DepositMade deposit -> new WalletBalanceState(deposit.newBalance(), true);
            case WithdrawalMade withdrawal -> new WalletBalanceState(withdrawal.newBalance(), true);
        };
    }
}

Query decisionModel = QueryBuilder.builder()
    .events(
        type(WalletOpened.class),
        type(DepositMade.class),
        type(WithdrawalMade.class)
    )
    .tag("wallet_id", walletId)
    .build();

ProjectionResult<WalletBalanceState> result =
    eventStore.project(decisionModel, new WalletBalanceStateProjector());
```

## Protected Write

```java
int newBalance = result.state().balance() - withdrawalAmount;

AppendEvent withdrawalEvent = AppendEvent.builder(type(WithdrawalMade.class))
    .tag("wallet_id", walletId)
    .tag("withdrawal_id", withdrawalId)
    .data(new WithdrawalMade(withdrawalId, walletId, withdrawalAmount, newBalance))
    .build();

eventStore.appendNonCommutative(
    List.of(withdrawalEvent),
    decisionModel,
    result.streamPosition()
);
```

If another matching event was appended after `result.streamPosition()`, Crablet throws `ConcurrencyException` and the caller retries from a fresh projection.

In other words: the write is only valid if the exact state you based the decision on is still current.

That is the DCB boundary:

- read the exact events that matter for the decision
- write only if nothing relevant changed meanwhile

## Checkpoint

After this part, you should understand why `appendNonCommutative(...)` needs both:

- the query that defines the decision boundary
- the stream position captured from the read

Expected result:

- concurrent state-dependent writes are rejected when the decision model changed
- callers retry from a fresh projection instead of silently overwriting each other

## Next

Continue with [Part 4: Views](04-views.md).
