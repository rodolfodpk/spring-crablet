# Part 4: Views

This tutorial introduces `crablet-views`.

You will learn:

- how to project asynchronous read models
- how subscriptions define which events wake a view
- how the event poller runs view processors

## Enable Views

```properties
crablet.views.enabled=true
crablet.views.polling-interval-ms=1000
crablet.views.batch-size=100
```

## Create A Projector

```java
@Component
public class WalletViewProjector extends AbstractTypedViewProjector<WalletEvent> {

    public WalletViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    @Override
    public String getViewName() {
        return "wallet-view";
    }

    @Override
    protected Class<WalletEvent> getEventType() {
        return WalletEvent.class;
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

## Deployment Guidance

`crablet-views` uses `crablet-event-poller`.

Recommended production shape:

- run **1 instance** in the normal case
- run **2 instances at most** when you want active/failover behavior

Do not scale views horizontally expecting higher throughput from the same processors. Leader election means only one instance is actively projecting a given processor set.

## Next

Continue with [Part 5: Automations](05-automations.md).
