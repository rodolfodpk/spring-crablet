# Part 1: Event Store Basics

This tutorial introduces `crablet-eventstore` only.

Canonical compile fixture:
[Part 1 compile fixture](../../../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial01EventStoreBasicsSample.java)

## Why This Part Exists

Before commands, DCB, views, or automations, you need the basic loop:

- append an event
- query the event log
- project current state from past events

That is the foundation for everything else in Crablet.

Skip this part if you already understand basic event-sourcing read/write flow and only want to see how Crablet models command execution and concurrency.

You will learn:

- how to append your first event
- how to tag events
- how to project state back from the event log

Assume this import in the snippets below:

```java
import static com.crablet.eventstore.EventType.type;
```

## Domain

```java
public sealed interface WalletEvent permits WalletOpened, DepositMade, WithdrawalMade {}

public record WalletOpened(String walletId, String owner, int initialBalance) implements WalletEvent {}
public record DepositMade(String depositId, String walletId, int amount, int newBalance) implements WalletEvent {}
public record WithdrawalMade(String withdrawalId, String walletId, int amount, int newBalance)
        implements WalletEvent {}
```

```java
public record WalletBalanceState(String walletId, int balance, boolean exists) {
    public static WalletBalanceState empty() {
        return new WalletBalanceState(null, 0, false);
    }
}
```

## Append An Event

```java
WalletOpened opened = new WalletOpened("wallet-1", "alice", 100);

AppendEvent appendEvent = AppendEvent.builder(type(WalletOpened.class))
    .tag("wallet_id", opened.walletId())
    .data(opened)
    .build();

eventStore.appendCommutative(List.of(appendEvent));
```

## Read It Back

```java
Query query = QueryBuilder.builder()
    .events(type(WalletOpened.class))
    .tag("wallet_id", "wallet-1")
    .build();

boolean exists = eventStore.exists(query);
```

## Project Full State

```java
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
        return WalletBalanceState.empty();
    }

    @Override
    public WalletBalanceState transition(
            WalletBalanceState state,
            StoredEvent event,
            EventDeserializer deserializer) {
        WalletEvent walletEvent = deserializer.deserialize(event, WalletEvent.class);
        return switch (walletEvent) {
            case WalletOpened opened -> new WalletBalanceState(opened.walletId(), opened.initialBalance(), true);
            case DepositMade deposit -> new WalletBalanceState(deposit.walletId(), deposit.newBalance(), true);
            case WithdrawalMade withdrawal -> new WalletBalanceState(withdrawal.walletId(), withdrawal.newBalance(), true);
        };
    }
}

ProjectionResult<WalletBalanceState> result =
    eventStore.project(query, new WalletBalanceStateProjector());
```

`ProjectionResult` gives you:

- the projected state
- the `StreamPosition` of the last event you read

That stream position becomes important in Part 3.

## Checkpoint

After this part, you should understand the basic event-sourcing loop in Crablet:

- write facts as events
- find the relevant events by query and tags
- rebuild current state by projecting those events

Expected result:

- `exists` is `true`
- `result.state().walletId()` is `wallet-1`
- `result.state().balance()` is `100`

## Next

Continue with [Part 2: Commands](02-commands.md).
