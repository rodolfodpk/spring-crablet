# Crablet Automations

Light framework component for event-driven automations (policies) with Spring Boot integration.

## Overview

Crablet Automations implements the "when X happens, do Y" pattern from event storming — also known as policies or process managers. When a domain event is stored, an automation can listen for it and automatically execute one or more commands.

## Recommended Pattern

Crablet automations should follow an Event Modeling-style automation pattern:

- **Command handlers persist facts**. They should not perform external side effects such as HTTP calls, email delivery, or message publication.
- **Views model decision state**. The automation decision should be based on the current view model, not on raw event occurrence alone.
- **Automations perform side effects**. An automation may be triggered by events, but it should evaluate modeled state, call external services through injected gateways/clients, and then record the outcome through the normal command/event flow.

Short version:

- command handlers write facts
- views model decision state
- automations read modeled state
- automations perform side effects

This separation keeps transactional consistency concerns in command handling and moves non-transactional work to automations, where retries and idempotency are expected.

**Key Features:**
- Shared matching contract via `AutomationDefinition`
- In-process automations via `AutomationHandler`
- Webhook automations via `AutomationSubscription`
- Tag-based and event-type-based matching
- At-least-once semantics with idempotency via DCB checks
- Independent progress tracking per automation
- Leader election: only one instance processes per automation
- Auto-configuration via `crablet.automations.enabled=true`

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-automations</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-eventstore (required)
- crablet-event-poller (required)
- crablet-commands (required — automations execute commands)
- Spring Boot JDBC

## Quick Start

### 1. Enable Automations

```properties
# application.properties
crablet.automations.enabled=true
crablet.automations.polling-interval-ms=1000
crablet.automations.batch-size=100
```

### 2. Choose an Automation Style

Crablet automations support two delivery styles:

- `AutomationHandler` for in-process work in the same JVM
- `AutomationSubscription` for webhook delivery to HTTP endpoints

Both styles share the same matching contract through `AutomationDefinition`:

- `getAutomationName()`
- `getEventTypes()`
- `getRequiredTags()`
- `getAnyOfTags()`

Use `AutomationHandler` when the automation should execute commands or call injected collaborators directly.

### 3. Implement AutomationHandler

Treat the incoming event as a trigger, not as the decision source. The automation should load the current view-model state, decide from that state whether work is needed, and then execute the next command.

```java
@Component
public class WelcomeNotificationAutomation implements AutomationHandler {

    private static final String AUTOMATION_NAME = "welcome-notification";

    private final WelcomeNotificationViewRepository viewRepository;

    public WelcomeNotificationAutomation(WelcomeNotificationViewRepository viewRepository) {
        this.viewRepository = viewRepository;
    }

    @Override
    public String getAutomationName() {
        return AUTOMATION_NAME;
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

### 4. Or Declare a Webhook Automation

Use `AutomationSubscription` when matching events should be delivered over HTTP instead of handled in-process:

```java
@Bean
public AutomationSubscription walletOpenedWebhookAutomation() {
    return AutomationSubscription.builder("welcome-notification-webhook")
        .eventTypes(EventType.type(WalletOpened.class))
        .requiredTags("wallet_id")
        .webhookUrl("http://localhost:8080/api/automations/wallet-opened")
        .build();
}
```

### 5. Registration Rules

Each automation name must be unique across the application:

- two `AutomationHandler` beans cannot share the same name
- two `AutomationSubscription` beans cannot share the same name
- a handler and a subscription cannot share the same name

Invalid overlap now fails fast at startup.

### 6. Database Migration

Add the `reaction_progress` table to your application's Flyway migrations:

```sql
CREATE TABLE IF NOT EXISTS reaction_progress (
    reaction_name VARCHAR(255) PRIMARY KEY,
    instance_id   VARCHAR(255),
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_position BIGINT NOT NULL DEFAULT 0,
    error_count   INTEGER NOT NULL DEFAULT 0,
    last_error    TEXT,
    last_error_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## Core Concepts

### AutomationDefinition

`AutomationDefinition` is the shared matching contract for automations:

```java
public interface AutomationDefinition {
    String getAutomationName();
    Set<String> getEventTypes();
    Set<String> getRequiredTags();
    Set<String> getAnyOfTags();
}
```

`AutomationHandler` and `AutomationSubscription` both implement this contract.

### AutomationHandler

```java
public interface AutomationHandler extends AutomationDefinition {
    /** Unique name — must not overlap any AutomationSubscription. */
    String getAutomationName();

    /** Matching filters come from AutomationDefinition. */
    Set<String> getEventTypes();

    /** Called once per matching event. Use the event as a trigger, then decide from modeled state. */
    void react(StoredEvent event, CommandExecutor commandExecutor);
}
```

The event tells the automation that something changed. The automation should then read the relevant view model and decide from that modeled state whether it should act.

### AutomationSubscription

Defines a webhook automation: matching rules plus HTTP delivery details.

```java
public class AutomationSubscription implements AutomationDefinition {
    String getAutomationName();
    Set<String> getEventTypes();
    Set<String> getRequiredTags();
    Set<String> getAnyOfTags();
    String getWebhookUrl();
}
```

Examples:

```java
// Subscribe to all WalletOpened events
AutomationSubscription.builder("my-automation")
    .eventTypes("WalletOpened")
    .webhookUrl("http://localhost:8080/webhook")
    .build();

// Subscribe only to events with a specific tag
AutomationSubscription.builder("my-automation")
    .eventTypes("DepositMade")
    .requiredTags("wallet_id")   // ALL of these tags must be present
    .anyOfTags("region-us", "region-eu") // AT LEAST ONE must be present
    .webhookUrl("http://localhost:8080/webhook")
    .build();
```

### View-Model-Driven Decisions

Recommended automation flow:

1. A matching event wakes up the automation
2. The automation loads the relevant view model
3. The automation decides from the current modeled state whether action is needed
4. The automation executes the next command or calls an external gateway
5. The outcome is recorded through the normal command/event flow

This keeps automations aligned with Event Modeling: events are triggers, while the decision is based on current state.

### Idempotency

Automations run with at-least-once semantics — the same event may trigger `react()` more than once (e.g., after a crash). Protect against duplicate work by making the downstream command or side-effect path idempotent:

```java
// In the downstream command handler:
AppendCondition condition = AppendCondition.idempotent(type(WelcomeNotificationSent.class), WALLET_ID, walletId);
```

This ensures the downstream event is recorded at most once regardless of how many times the automation fires.

## Architecture

```
Events table (PostgreSQL)
    ↓  polling (configurable interval)
AutomationEventFetcher  — filters by automation definition (eventTypes, tags)
    ↓
AutomationDispatcher  — routes each event to the matching handler or webhook
    ↓
AutomationHandler.react() / webhook POST
    ↓
CommandExecutor / external gateway
    ↓
reaction_progress     — progress tracked per automation name
```

- **Leader election**: PostgreSQL advisory locks ensure only one instance processes each automation at a time
- **Progress tracking**: Each automation independently tracks its last processed event position
- **Backoff**: Exponential backoff on consecutive errors, up to `max-backoff-seconds`

## Configuration

```properties
# Enable automations
crablet.automations.enabled=true

# Polling interval in milliseconds
crablet.automations.polling-interval-ms=1000

# Batch size for event processing
crablet.automations.batch-size=100

# Backoff configuration
crablet.automations.backoff-threshold=10
crablet.automations.backoff-multiplier=2
crablet.automations.max-backoff-seconds=60
```

## Examples

The `shared-examples-domain` module contains a complete working example:

- **`WalletOpenedReaction`** (in `wallet-example-app`) — in-process `AutomationHandler` for `WalletOpened`
- **`SendWelcomeNotificationCommandHandler`** — records a `WelcomeNotificationSent` event with an idempotency check

The recommended pattern is to keep the decision in a view model and use the automation to bridge from trigger to side effect or follow-up command.

## See Also

- [EventStore README](../crablet-eventstore/README.md) — Core event sourcing library
- [Command README](../crablet-commands/README.md) — Command framework
- [Event Processor README](../crablet-event-poller/README.md) — Generic polling infrastructure
- [Views README](../crablet-views/README.md) — Asynchronous view projections
