# Part 5: Automations

This tutorial introduces `crablet-automations`.

Canonical compile fixture:
[Part 5 compile fixture](../../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial05AutomationsSample.java)

## Why This Part Exists

Commands and views handle transactional decisions and read models.

Automations handle the next class of work:

- reacting to domain events
- running follow-up commands
- coordinating internal application behavior outside the original transaction

Skip this part if your current goal is only event storage plus read models.

You will learn:

- how to react to stored events
- why events should be treated as triggers, not full decision state
- how to execute follow-up application work from an automation

Use `crablet-outbox` instead when stored events need to be exported to external systems such as HTTP webhooks, Kafka, analytics, or CRM integrations.

Automation webhook mode has been removed. If you previously used an automation webhook to call local application code, move that behavior into `decide()` and return an `AutomationDecision.ExecuteCommand`. If you used it to call an external HTTP endpoint, implement an `OutboxPublisher` instead.

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

## Shared-Fetch Mode

By default each automation runs its own DB query per polling cycle. If you have many automations and want to reduce DB load on LISTEN/NOTIFY wakeups, enable the shared-fetch path:

```properties
crablet.automations.shared-fetch.enabled=true
crablet.automations.fetch-batch-size=1000
```

Shared-fetch uses one position-only DB fetch per module cycle, then routes matching events to each automation in memory. `fetch-batch-size` controls the shared DB read size. `batch-size` still controls how many matched events each automation handles per cycle.

Shared-fetch requires the scan-progress tables from the V14-style migration used by the example app. Leave the flag unset or `false` if your application has not added those tables.

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
    public Long getPollingIntervalMs() {
        return 500L;
    }

    @Override
    public Integer getBatchSize() {
        return 25;
    }

    @Override
    public List<AutomationDecision> decide(StoredEvent event) {
        String walletId = event.tags().stream()
            .filter(tag -> tag.key().equals("wallet_id"))
            .map(Tag::value)
            .findFirst()
            .orElseThrow();

        WelcomeNotificationView view = viewRepository.get(walletId);
        if (view.shouldSendWelcomeNotification()) {
            return List.of(new AutomationDecision.ExecuteCommand(
                    SendWelcomeNotificationCommand.of(walletId)));
        }
        return List.of(new AutomationDecision.NoOp("welcome notification not needed"));
    }
}
```

Each `AutomationHandler` is also the per-poller-instance config for that automation. It defines matching rules and can override polling interval, batch size, and backoff settings for that one automation.

Treat the event as a wake-up signal. The automation should still read current modeled state before deciding what to do.

## Deployment Guidance

`crablet-automations` also uses `crablet-event-poller`.

Recommended production shape:

- run **1 application instance per cluster** for the simplest topology
- if automations need isolation, run one singleton automations worker service

More replicas do not make the same automation processors run faster. They mainly add standby behavior while one elected leader does the work.

## Checkpoint

After this part, you should understand how Crablet separates transactional facts from non-transactional follow-up work.

Expected result:

- a stored `WalletOpened` event can wake an automation
- the automation can read a view model to decide whether work is needed
- the automation can return a follow-up command decision for the dispatcher to execute through `CommandExecutor`

Use `crablet-outbox` when stored events need to be published to external systems such as HTTP webhooks, Kafka, analytics, or CRM integrations.

## Next

Continue with [Part 6: Outbox](06-outbox.md).
