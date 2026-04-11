# Part 5: Automations

This tutorial introduces `crablet-automations`.

You will learn:

- how to react to stored events
- why events should be treated as triggers, not full decision state
- how to execute in-process work or webhook delivery from the same automation definition

## Enable Automations

```properties
crablet.automations.enabled=true
crablet.automations.polling-interval-ms=1000
crablet.automations.batch-size=100
```

## In-Process Automation

```java
@Component
public class WelcomeNotificationAutomation implements AutomationHandler {

    private final WelcomeNotificationViewRepository viewRepository;

    public WelcomeNotificationAutomation(WelcomeNotificationViewRepository viewRepository) {
        this.viewRepository = viewRepository;
    }

    @Override
    public String getAutomationName() {
        return "welcome-notification";
    }

    @Override
    public void react(StoredEvent event, CommandExecutor commandExecutor) {
        String walletId = ...;
        WelcomeNotificationView view = viewRepository.get(walletId);
        if (view.shouldSendWelcomeNotification()) {
            commandExecutor.execute(SendWelcomeNotificationCommand.of(walletId));
        }
    }
}
```

## Webhook Delivery

```java
@Override
public String getWebhookUrl() {
    return "http://localhost:8080/api/automations/wallet-opened";
}
```

## Deployment Guidance

`crablet-automations` also uses `crablet-event-poller`.

Recommended production shape:

- run **1 instance** in the normal case
- run **2 instances at most** for active/failover behavior

More replicas do not make the same automation processors run faster. They mainly give you standby capacity while one elected leader does the work.

## Next

Continue with [Part 6: Outbox](06-outbox.md).
