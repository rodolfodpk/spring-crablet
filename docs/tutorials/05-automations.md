# Part 5: Automations

This tutorial introduces `crablet-automations`.

Canonical compile fixture:
[docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial05AutomationsSample.java](../../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial05AutomationsSample.java)

## Why This Part Exists

Commands and views handle transactional decisions and read models.

Automations handle the next class of work:

- reacting to domain events
- running follow-up commands
- triggering external side effects outside the original transaction

Skip this part if your current goal is only event storage plus read models.

You will learn:

- how to react to stored events
- why events should be treated as triggers, not full decision state
- how to execute in-process work or webhook delivery from the same automation definition

Assume this import in the snippets below:

```java
import static com.crablet.eventstore.EventType.type;
```

## Enable Automations

```properties
crablet.automations.enabled=true
crablet.automations.polling-interval-ms=1000
crablet.automations.batch-size=100
```

`crablet.automations.*` is the global module config. These values are defaults for all automation processors.

## In-Process Automation

```java
public record WelcomeNotificationView(String walletId, boolean shouldSendWelcomeNotification) {}

public interface WelcomeNotificationViewRepository {
    WelcomeNotificationView get(String walletId);
}

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
    public Set<String> getEventTypes() {
        return Set.of(type(WalletOpened.class));
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of("wallet_id");
    }

    @Override
    public void react(StoredEvent event, CommandExecutor commandExecutor) {
        String walletId = event.tags().stream()
            .filter(tag -> tag.key().equals("wallet_id"))
            .map(Tag::value)
            .findFirst()
            .orElseThrow();

        WelcomeNotificationView view = viewRepository.get(walletId);
        if (view.shouldSendWelcomeNotification()) {
            commandExecutor.execute(SendWelcomeNotificationCommand.of(walletId));
        }
    }
}
```

Each `AutomationHandler` is also the per-poller-instance config for that automation. It defines matching rules and can override polling and backoff settings for that one automation.

Treat the event as a wake-up signal. The automation should still read current modeled state before deciding what to do.

## Webhook Delivery

```java
@Component
public class WelcomeNotificationWebhookAutomation implements AutomationHandler {

    @Override
    public String getAutomationName() {
        return "welcome-notification-webhook";
    }

    @Override
    public Set<String> getEventTypes() {
        return Set.of(type(WalletOpened.class));
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of("wallet_id");
    }

    @Override
    public String getWebhookUrl() {
        return "http://localhost:8080/webhooks/wallet-opened";
    }
}
```

## Deployment Guidance

`crablet-automations` also uses `crablet-event-poller`.

Recommended production shape:

- run **1 application instance per cluster**

More replicas do not make the same automation processors run faster. They mainly add standby behavior while one elected leader does the work.

## Checkpoint

After this part, you should understand how Crablet separates transactional facts from non-transactional follow-up work.

Expected result:

- a stored `WalletOpened` event can wake an automation
- the automation can read a view model to decide whether work is needed
- the automation can either dispatch a follow-up command or deliver the event to a webhook

## Next

Continue with [Part 6: Outbox](06-outbox.md).
