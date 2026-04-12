# Part 4: Views

This tutorial introduces `crablet-views`.

Canonical compile fixture:
[docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial04ViewsSample.java](../../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial04ViewsSample.java)

## Why This Part Exists

So far, the tutorials focused on the write side:

- commands decide
- events are stored
- state is projected in memory for business decisions

Views are different. They persist read models to database tables so queries can be fast and simple.

Skip this part if you only care about the write side and do not plan to materialize read models yet.

You will learn:

- how to project asynchronous read models
- how subscriptions define which events wake a view
- how the event poller runs view processors

Assume this import in the snippets below:

```java
import static com.crablet.eventstore.EventType.type;
```

## Enable Views

```properties
crablet.views.enabled=true
crablet.views.polling-interval-ms=1000
crablet.views.batch-size=100
```

`crablet.views.*` is the global module config. These values are defaults for all view processors.

## Create A Projector

```java
public sealed interface WalletEvent permits WalletOpened, DepositMade, WithdrawalMade {}

@Component
public class WalletViewProjector extends AbstractTypedViewProjector<WalletEvent> {

    public WalletViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager,
            WriteDataSource writeDataSource) {
        super(objectMapper, clockProvider, transactionManager, writeDataSource);
    }

    @Override
    public String getViewName() {
        return "wallet-view";
    }

    @Override
    protected Class<WalletEvent> getEventType() {
        return WalletEvent.class;
    }

    @Override
    protected boolean handleEvent(WalletEvent event, StoredEvent storedEvent, JdbcTemplate jdbc) {
        return switch (event) {
            case WalletOpened opened -> {
                jdbc.update(
                    "insert into wallet_view (wallet_id, balance) values (?, ?) " +
                    "on conflict (wallet_id) do update set balance = excluded.balance",
                    opened.walletId(),
                    opened.initialBalance()
                );
                yield true;
            }
            case DepositMade deposit -> {
                jdbc.update(
                    "update wallet_view set balance = ? where wallet_id = ?",
                    deposit.newBalance(),
                    deposit.walletId()
                );
                yield true;
            }
            case WithdrawalMade withdrawal -> {
                jdbc.update(
                    "update wallet_view set balance = ? where wallet_id = ?",
                    withdrawal.newBalance(),
                    withdrawal.walletId()
                );
                yield true;
            }
        };
    }
}
```

## Subscribe

```java
@Bean
public ViewSubscription walletViewSubscription(WalletViewProjector projector) {
    return projector.subscription(
        type(WalletOpened.class),
        type(DepositMade.class),
        type(WithdrawalMade.class)
    );
}
```

`ViewSubscription` is the per-poller-instance config for this view. It defines event selection and can override polling interval, batch size, and backoff settings for this one view.

That means one view is one independently managed processor with its own progress and tuning.

## Deployment Guidance

`crablet-views` uses `crablet-event-poller`.

Recommended production shape:

- run **1 application instance per cluster**

Do not scale views horizontally expecting higher throughput from the same processors. Leader election means only one instance is actively projecting a given processor set.

## Checkpoint

After this part, you should understand the role of views:

- command-side projections are for business decisions
- views are for persisted read models
- subscriptions define which events wake a given view processor

Expected result:

- a `wallet_view` row exists after `WalletOpened`
- deposits and withdrawals update that row asynchronously
- API reads can use `wallet_view` instead of replaying the event log on every request

## Next

Continue with [Part 5: Automations](05-automations.md).
