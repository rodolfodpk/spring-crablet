# Crablet Automations

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_automations)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light framework component for event-driven automations (policies) with Spring Boot integration.

## Positioning

`crablet-automations` is a poller-backed add-on.

It should come after the command side, not before it. For learning, one application instance running commands and automations together is the clearest setup. For production, default to one application instance per cluster for the simplest topology, or run automations as their own singleton worker service.

## Start Here

- Adopt `crablet-eventstore` and `crablet-commands` first
- Add automations when you need follow-up application behavior after events are stored
- In this README, focus on `Recommended Pattern`, `Quick Start`, and `Configuration`

## Overview

Crablet Automations implements an Event Modeling-style reaction pattern: when a domain event is stored, an automation can read modeled decision state and automatically execute one or more commands.

## Deployment Recommendation

`crablet-automations` is built on `crablet-event-poller`.

Recommended production shape:

- run **1 application instance per cluster** in the simplest case
- if automations need isolation, run one singleton automations worker service with one elected active automations poller

The poller elects one active leader for the automation processors, so additional replicas do not make the same automations process faster; they mainly add standby behavior and operational complexity.

## Recommended Pattern

Crablet automations should follow an Event Modeling-style automation pattern:

- **Command handlers persist facts**. They should not perform external publication or other non-transactional side effects.
- **Modeled state drives decisions**. The automation decision should be based on current TODO/read-model state, not on raw event occurrence alone.
- **Automations perform application reactions**. An automation may be triggered by events, but it should evaluate modeled state, call injected application services/gateways when needed, and then usually record the outcome by executing a command.
- **Outbox publishes externally**. Use `crablet-outbox` when stored events need to leave the application boundary through application-provided publisher implementations.

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
- crablet-views (optional — enables view-backed wake-event inference; see [ViewBackedAutomationHandler](#viewbackedautomationhandler))
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
| Publish stored events outside the application boundary | Outbox |
| Fan out a topic to multiple publisher implementations | Outbox |
| Track publication progress independently from automations | Outbox |

Crablet automations use a single public definition type, `AutomationHandler`, which shares matching rules through `AutomationDefinition`:

- `getAutomationName()`
- `getEventTypes()`
- `getRequiredTags()`
- `getAnyOfTags()`

Use `AutomationHandler` when the automation should describe follow-up commands to run in-process. For event publication outside the application boundary, implement an `OutboxPublisher`.

### 3. Implement AutomationHandler

Treat the incoming event as a trigger, not as the decision source. The automation should load current modeled decision state, decide whether work is needed, and then return the next command as a decision.

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

`WelcomeNotificationViewRepository` is an application abstraction. It can be backed by `crablet-views`, a custom projection, or any application data source that represents the modeled decision state.

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

Automations use the shared poller [Event Selection](../crablet-event-poller/README.md#event-selection) semantics: event types and tag filters combine with AND, and empty sets mean unrestricted for that dimension.

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

The event tells the automation that something changed. The automation should then read modeled decision state and decide from that state whether it should act.

`AutomationDecision` currently supports:

- `ExecuteCommand(Object command)` — dispatch a follow-up command through `CommandExecutor`
- `NoOp(String reason)` — explicitly record that no action is needed

Prefer returning `ExecuteCommand` for internal follow-up behavior instead of launching background
work from the handler. The dispatcher executes decisions synchronously inside the triggering
event's correlation/causation scope, so events emitted by the follow-up command keep the trace.
If an application deliberately submits custom async work or appends events outside the dispatcher
scope, it owns correlation/causation propagation for that work. For event publication outside the
application boundary, use `crablet-outbox`.

### Modeled-State Decisions

Recommended automation flow:

1. A matching event wakes up the automation
2. The automation loads current TODO/read-model state from a Crablet view, custom projection, or application repository
3. The automation decides from the current modeled state whether action is needed
4. The automation returns `ExecuteCommand` or `NoOp`
5. The outcome is recorded through the normal command/event flow

This keeps automations aligned with Event Modeling: events are triggers, while the decision is based on current state.

### ViewBackedAutomationHandler

`ViewBackedAutomationHandler` is an optional extension for automations whose wake-event set should match exactly the events a view already reads. Instead of declaring `getEventTypes()` manually, the framework infers it from the referenced view's `ViewSubscription` at startup.

Use it when your automation reacts to any state change tracked by a view and you want to avoid keeping two event-type lists in sync.

**Requirements:** `crablet-views` must be on the classpath (declared as an optional dependency in `pom.xml`) and the referenced `ViewSubscription` beans must be registered. The views processor does not need to be enabled in the same process; this supports distributed deployments where a views worker projects read models and an automations worker only runs automations.

```java
// 1. Declare the interface (codegen-generated)
public interface EnrollmentAutomationHandler extends ViewBackedAutomationHandler {

    @Override
    default String getAutomationName() { return "enrollment-automation"; }

    @Override
    default Set<String> getReadViewNames() { return Set.of("enrollment_todo"); }
}

// 2. Implement it
@Component
public class EnrollmentAutomation implements EnrollmentAutomationHandler {

    @Override
    public List<AutomationDecision> decide(StoredEvent event) {
        // load enrollment_todo view, decide from state
        return List.of(new AutomationDecision.ExecuteCommand(enrollCommand));
    }
}
```

For fine-grained control, two optional overrides are available:

| Method | Purpose |
|--------|---------|
| `getWakeEventsExtra()` | Additional event types to wake on, beyond those inferred from views |
| `getWakeEventsExclude()` | Event types to remove from the inferred set |

Do not override `getEventTypes()` on a `ViewBackedAutomationHandler` — it throws `UnsupportedOperationException` intentionally.

**Codegen (`event-model.yaml`):** Use `readsViews` (list), `wakeEventsExtra`, and `wakeEventsExclude` in an automation spec to generate `ViewBackedAutomationHandler` interfaces. The preferred form is `readsViews` (plural). The single-view `readsView` field is still accepted for backward compatibility and treated as a one-element `readsViews`; new models and generated output should prefer `readsViews`.

**Pure Java users do not need `event-model.yaml`.** The `ViewBackedAutomationHandler` interface is used directly in Java code as shown above.

### Retry Safety and Failure Semantics

**Independent progress.** Views, automations, and outbox each track their own position. A view can successfully project an event while the automation that handles the same event fails. View projection does not block or wait for automations. Each consumer retries from its own last known position:

```text
events table
  → view processor updates read model and advances view_progress
  → automation processor sees matching event and calls decide()
  → automation reads read model
  → automation emits command or no-ops
  → automation_progress advances only after all decisions succeed
```

**At-least-once delivery.** The automation retries an event if `decide()` or any of its decisions fail. Progress only advances after every decision in a batch succeeds.

**Known hazard: crash after command success.** There is a window between a command executing successfully and the automation progress being committed:

```text
Automation executes command successfully.
Process crashes before automation_progress advances.
Automation retries the same event on restart.
The emitted command must detect the duplicate and return idempotent or throw according to policy.
```

**Automation commands must be duplicate-safe.** Use a stable business key (not a random ID) as the idempotency tag. The new `.idempotent(...)` method on `CommandDecision` variants makes this explicit:

```java
// Commutative command with duplicate protection — safe for automation retry
AppendEvent event = AppendEvent.builder(type(WelcomeNotificationSent.class))
        .tag(WALLET_ID, command.walletId())
        .data(notification)
        .build();

return CommandDecision.Commutative.of(event)
        .idempotent(type(WelcomeNotificationSent.class), WALLET_ID, command.walletId());
```

The idempotency key uses the `wallet_id` tag — a stable business key that is the same on every retry. The second execution returns `ExecutionResult.idempotent(...)` silently instead of appending a duplicate event.

For creation-style commands that should fail on a duplicate:

```java
return CommandDecision.Commutative.of(event)
        .idempotent(type(WelcomeNotificationSent.class), WALLET_ID, command.walletId(),
                OnDuplicate.THROW);
```

Pure Java users do not need `event-model.yaml` — the `.idempotent(...)` API is available directly on `Commutative` and `CommutativeGuarded` decisions.

**Non-commutative commands need a different pattern.** `.idempotent(...)` does not exist on `NonCommutative` and would not work reliably if it did: non-commutative handlers have business pre-condition guards (balance checks, capacity checks) that run before the handler returns any decision. On retry, those guards often fail for legitimate reasons (e.g., a withdrawal handler that sees an already-reduced balance throws `InsufficientFundsException`), so the store-level check is never reached. Use the `NoOp` pre-check pattern instead:

```java
// Non-commutative handler — check for duplicate FIRST, before business guards
if (eventStore.exists(Query.forEventAndTag(type(WithdrawalMade.class), WITHDRAWAL_ID, command.withdrawalId()))) {
    return CommandDecision.NoOp.empty();
}
// ... project state, validate balance, build event, return NonCommutative ...
```

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
- **Failure handling**: Failed decisions do not advance progress, so the event is redelivered until `max-errors` marks the automation `FAILED`
- **Trace propagation**: `ExecuteCommand` decisions run inside the triggering event's correlation/causation scope; custom async work must carry that context explicitly
- **App-owned resilience**: Crablet does not ship a built-in circuit breaker; add one in the application-owned `AutomationHandler` when a policy calls unreliable external dependencies

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

Shared-fetch changes query shape, not which events match an automation. Matching still follows the shared poller [Event Selection](../crablet-event-poller/README.md#event-selection) contract.

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

The recommended pattern is to keep the decision in modeled TODO/read state and use the automation to bridge from trigger to side effect or follow-up command.

## See Also

- [EventStore README](../crablet-eventstore/README.md) — Core event sourcing library
- [Command README](../crablet-commands/README.md) — Command framework
- [Event Processor README](../crablet-event-poller/README.md) — Shared processing infrastructure behind automations
- [Views README](../crablet-views/README.md) — Asynchronous view projections
