# Crablet Automations

Light framework component for event-driven automations (policies) with Spring Boot integration.

## Positioning

`crablet-automations` is a poller-backed add-on.

It should come after the command side, not before it. For learning, one application instance running commands and automations together is the clearest setup. For production, default to one application instance per cluster for the simplest topology, or run automations as their own singleton worker service.

## Start Here

- Adopt `crablet-eventstore` and `crablet-commands` first
- Add automations when you need follow-up application behavior after events are stored
- In this README, focus on `Recommended Pattern`, `Quick Start`, and `Configuration`

## Overview

Crablet Automations implements the "when X happens, do Y" pattern from event storming — also known as policies or process managers. When a domain event is stored, an automation can listen for it and automatically execute one or more commands.

## Deployment Recommendation

`crablet-automations` is built on `crablet-event-poller`.

Recommended production shape:

- run **1 application instance per cluster** in the simplest case
- if automations need isolation, run one singleton automations worker service with one elected active automations poller

The poller elects one active leader for the automation processors, so additional replicas do not make the same automations process faster; they mainly add standby behavior and operational complexity.

## Recommended Pattern

Crablet automations should follow an Event Modeling-style automation pattern:

- **Command handlers persist facts**. They should not perform external side effects such as HTTP calls, email delivery, or message publication.
- **Views model decision state**. The automation decision should be based on the current view model, not on raw event occurrence alone.
- **Automations perform application reactions**. An automation may be triggered by events, but it should evaluate modeled state, call injected application services/gateways when needed, and then usually record the outcome by executing a command.
- **Outbox publishes externally**. Use `crablet-outbox` when stored events need to leave the application boundary, including external HTTP webhooks, Kafka, analytics, or CRM integrations.

Short version:

- command handlers write facts
- views model decision state
- automations read modeled state
- automations perform follow-up application behavior
- outbox exports events to external systems

This separation keeps transactional consistency concerns in command handling and moves non-transactional work to automations, where retries and idempotency are expected.

**Key Features:**
- Shared matching contract via `AutomationDefinition`
- Unified automations via `AutomationHandler`
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

### 2. Choose the Right Module

Use automations when an event should trigger application behavior. Use outbox when stored events should be exported to other systems.

| Scenario | Use |
| --- | --- |
| Send welcome notification command after `WalletOpened` | Automation |
| Close monthly statement when `StatementPeriodEnded` | Automation |
| Check balance and issue `FlagLargeDepositCommand` | Automation |
| Publish `WalletOpened` to customer.io | Outbox |
| Send all payment events to Kafka | Outbox |
| Send `DepositMade` to analytics API | Outbox |

Crablet automations use a single public definition type, `AutomationHandler`, which shares matching rules through `AutomationDefinition`:

- `getAutomationName()`
- `getEventTypes()`
- `getRequiredTags()`
- `getAnyOfTags()`

Use `AutomationHandler` when the automation should describe follow-up commands to run in-process. For external HTTP webhooks or event publication, implement an `OutboxPublisher`.

### Migration Note: Webhook Mode Removed

`AutomationHandler` no longer supports webhook delivery methods such as `getWebhookUrl()`, `getWebhookHeaders()`, or `getWebhookTimeoutMs()`. Automations now have one execution path: implement `decide(StoredEvent)` and return `AutomationDecision` values.

If an old automation webhook called back into the same application, replace it with a normal `decide()` implementation and return an `AutomationDecision.ExecuteCommand` for the equivalent command.

If an old automation webhook published an event to an external HTTP endpoint, replace it with an `OutboxPublisher`. Use `PublishMode.INDIVIDUAL` when the external endpoint expects one event per request.

### 3. Implement AutomationHandler

Treat the incoming event as a trigger, not as the decision source. The automation should load the current view-model state, decide from that state whether work is needed, and then return the next command as a decision.

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

### 4. Registration Rules

Each `AutomationHandler` bean must have a unique automation name. Invalid overlap fails fast at startup.

### 5. Database Migration

Add the automation progress table to your application's Flyway migrations:

```sql
CREATE TABLE IF NOT EXISTS automation_progress (
    automation_name VARCHAR(255) PRIMARY KEY,
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

`AutomationHandler` implements this contract and handles matching events through `decide()`.

### AutomationHandler

```java
public interface AutomationHandler extends AutomationDefinition {
    String getAutomationName();
    Set<String> getEventTypes();
    Long getPollingIntervalMs(); // optional
    Integer getBatchSize(); // optional
    List<AutomationDecision> decide(StoredEvent event);
}
```

The event tells the automation that something changed. The automation should then read the relevant view model and decide from that modeled state whether it should act.

`AutomationDecision` currently supports:

- `ExecuteCommand(Object command)` — dispatch a follow-up command through `CommandExecutor`
- `NoOp(String reason)` — explicitly record that no action is needed

For external HTTP delivery, Kafka, analytics, CRM, or other event publication, use `crablet-outbox`.

### View-Model-Driven Decisions

Recommended automation flow:

1. A matching event wakes up the automation
2. The automation loads the relevant view model
3. The automation decides from the current modeled state whether action is needed
4. The automation returns `ExecuteCommand` or `NoOp`
5. The outcome is recorded through the normal command/event flow

This keeps automations aligned with Event Modeling: events are triggers, while the decision is based on current state.

### Idempotency

Automations run with at-least-once semantics — the same event may trigger `decide()` more than once (e.g., after a crash). Decisions are executed sequentially; if a later decision fails, earlier decisions may already have completed and the event may be retried. Protect against duplicate work by making downstream commands idempotent:

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
AutomationDispatcher  — routes each event to the matching handler
    ↓
AutomationHandler.decide()
    ↓
AutomationDecision.ExecuteCommand / NoOp
    ↓
CommandExecutor
    ↓
automation_progress   — schema table that stores per-automation progress
```

- **Leader election**: PostgreSQL advisory locks ensure only one instance processes each automation at a time
- **Progress tracking**: Each automation independently tracks its last processed event position
- **Backoff**: Exponential backoff on consecutive errors, up to `max-backoff-seconds`
- **Circuit breaker**: Each automation has its own Resilience4j circuit breaker (named `"automation-<automationName>"`). When the circuit opens, the automation is skipped with a WARN log and the poller's backoff continues. Configure thresholds in `application.yml` under `resilience4j.circuitbreaker.instances.automation-<name>`.

## Configuration

```properties
# Enable automations
crablet.automations.enabled=true

# Polling interval in milliseconds
crablet.automations.polling-interval-ms=1000

# Batch size for event processing
crablet.automations.batch-size=100

# Shared-fetch mode: one DB query per cycle serves all automations (default: false)
crablet.automations.shared-fetch.enabled=false

# Maximum events fetched per module cycle in shared-fetch mode (default: 1000)
crablet.automations.fetch-batch-size=1000

# Backoff configuration
crablet.automations.backoff-threshold=10
crablet.automations.backoff-multiplier=2
crablet.automations.max-backoff-seconds=60
```

`crablet.automations.*` is the global config for the automations module. It supplies defaults for every automation processor.

Each `AutomationHandler` is also the per-automation poller config. A handler can override polling interval, batch size, and backoff settings for that one automation while the rest keep the global defaults.

### Shared-Fetch Mode

When `crablet.automations.shared-fetch.enabled=true`, all automations in the module share a single position-only DB fetch per cycle. Events are routed in-memory to each automation using its `AutomationDefinition` matching rules. This reduces DB load on LISTEN/NOTIFY wakeups from N queries (one per automation) to one query per module cycle.

Requires two additional tables in your schema migration:

```sql
CREATE TABLE crablet_module_scan_progress (
    module_name   TEXT   PRIMARY KEY,
    scan_position BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE crablet_processor_scan_progress (
    module_name      TEXT   NOT NULL,
    processor_id     TEXT   NOT NULL,
    scanned_position BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (module_name, processor_id)
);
```

The legacy per-automation path (default) remains unchanged when the flag is absent or false.

Use `crablet.automations.fetch-batch-size` to tune how many events the shared module fetch reads per DB query. `crablet.automations.batch-size` still caps how many matched events each automation handles in one cycle. If individual handlers override `getBatchSize()`, that override is still respected.

## Examples

The repository contains a complete working example:

- **`WalletOpenedAutomation`** (in `wallet-example-app`) — in-process `AutomationHandler` for `WalletOpened`
- **`SendWelcomeNotificationCommandHandler`** — records a `WelcomeNotificationSent` event with an idempotency check

The recommended pattern is to keep the decision in a view model and use the automation to bridge from trigger to side effect or follow-up command.

## See Also

- [EventStore README](../crablet-eventstore/README.md) — Core event sourcing library
- [Command README](../crablet-commands/README.md) — Command framework
- [Event Processor README](../crablet-event-poller/README.md) — Shared processing infrastructure behind automations
- [Views README](../crablet-views/README.md) — Asynchronous view projections
