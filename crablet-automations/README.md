# Crablet Automations

Light framework component for event-driven automations (policies) with Spring Boot integration.

## Overview

Crablet Automations implements the "when X happens, do Y" pattern from event storming — also known as policies or process managers. When a domain event is stored, an automation can listen for it and automatically execute one or more commands.

**Key Features:**
- Implement `AutomationHandler` — one interface, two methods
- Tag-based and event-type-based subscriptions
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
- crablet-event-processor (required)
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

### 2. Implement AutomationHandler

```java
@Component
public class WalletOpenedAutomation implements AutomationHandler {

    private static final String AUTOMATION_NAME = "wallet-opened-welcome-notification";

    private final ObjectMapper objectMapper;

    public WalletOpenedAutomation(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAutomationName() {
        return AUTOMATION_NAME;
    }

    @Override
    public void react(StoredEvent event, CommandExecutor commandExecutor) {
        WalletEvent walletEvent = objectMapper.readValue(event.data(), WalletEvent.class);
        if (walletEvent instanceof WalletOpened opened) {
            commandExecutor.executeCommand(
                SendWelcomeNotificationCommand.of(opened.walletId(), opened.owner())
            );
        }
    }
}
```

### 3. Register a Subscription

Declare an `AutomationSubscription` bean that matches the automation's name and specifies which events trigger it:

```java
@Bean
public AutomationSubscription walletOpenedWelcomeNotificationSubscription() {
    return AutomationSubscription.builder("wallet-opened-welcome-notification")
        .eventTypes(EventType.type(WalletOpened.class))
        .build();
}
```

The subscription name must match `AutomationHandler.getAutomationName()` exactly.

### 4. Database Migration

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

### Automation Interface

```java
public interface AutomationHandler {
    /** Unique name — must match the corresponding AutomationSubscription. */
    String getAutomationName();

    /** Called once per matching event. Execute commands here. */
    void react(StoredEvent event, CommandExecutor commandExecutor);
}
```

### AutomationSubscription

Controls which events trigger an automation. Filters by event type and/or tags:

```java
// Subscribe to all WalletOpened events
AutomationSubscription.builder("my-automation")
    .eventTypes("WalletOpened")
    .build();

// Subscribe only to events with a specific tag
AutomationSubscription.builder("my-automation")
    .eventTypes("DepositMade")
    .requiredTags("wallet_id")   // ALL of these tags must be present
    .anyOfTags("region-us", "region-eu") // AT LEAST ONE must be present
    .build();
```

### Idempotency

Automations run with at-least-once semantics — the same event may trigger `react()` more than once (e.g., after a crash). Protect against duplicate side effects by using DCB idempotency checks in the command handler:

```java
// In your command handler:
AppendCondition condition = AppendCondition.idempotent(type(WelcomeNotificationSent.class), WALLET_ID, walletId);
```

This ensures the downstream event is recorded at most once regardless of how many times the automation fires.

## Architecture

```
Events table (PostgreSQL)
    ↓  polling (configurable interval)
AutomationEventFetcher  — filters by subscription (eventTypes, tags)
    ↓
AutomationDispatcher  — routes each event to the matching AutomationHandler
    ↓
Automation.react()      — executes commands via CommandExecutor
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

- **`WalletOpenedReaction`** (in `wallet-example-app`) — listens for `WalletOpened`, executes `SendWelcomeNotificationCommand`
- **`SendWelcomeNotificationCommandHandler`** — logs a welcome message and records a `WelcomeNotificationSent` event with an idempotency check

This demonstrates the full automation → command → event chain.

## See Also

- [EventStore README](../crablet-eventstore/README.md) — Core event sourcing library
- [Command README](../crablet-commands/README.md) — Command framework
- [Event Processor README](../crablet-event-processor/README.md) — Generic polling infrastructure
- [Views README](../crablet-views/README.md) — Asynchronous view projections
