# Automation Decisions Clean-Break Plan

## Goal

Move automations from imperative execution:

```java
void react(StoredEvent event, CommandExecutor commandExecutor)
```

to declarative decisions:

```java
List<AutomationDecision> decide(StoredEvent event)
```

This makes automation handlers describe what should happen while the automation dispatcher owns execution semantics, correlation/causation propagation, logging, metrics, and retry behavior.

This plan intentionally accepts a breaking API change. There is no migration shim.

## Target Design

Replace `AutomationHandler.react(...)` with `AutomationHandler.decide(...)` directly:

```java
public interface AutomationHandler extends AutomationDefinition, ProcessorRuntimeOverrides {

    @Override
    String getAutomationName();

    @Override
    Set<String> getEventTypes();

    List<AutomationDecision> decide(StoredEvent event);

    // existing tag and runtime override defaults stay
}
```

Add a small sealed decision hierarchy in `crablet-automations`:

```java
public sealed interface AutomationDecision
        permits AutomationDecision.ExecuteCommand,
                AutomationDecision.NoOp {

    record ExecuteCommand(Object command) implements AutomationDecision {}

    record NoOp(@Nullable String reason) implements AutomationDecision {
        public static NoOp empty() {
            return new NoOp(null);
        }
    }
}
```

Do not add `PublishToOutbox` in the first version. In this repository, outbox publishes stored events externally. Automations should execute commands, command handlers should append events, and outbox processors should publish those events.

## Implementation Plan

### 1. Add `AutomationDecision`

Create:

```text
crablet-automations/src/main/java/com/crablet/automations/AutomationDecision.java
```

Keep the hierarchy small:

- `ExecuteCommand`
- `NoOp`

This keeps automations aligned with the current architecture: automations orchestrate commands; outbox publishes stored events.

### 2. Change `AutomationHandler`

Replace:

```java
void react(StoredEvent event, @Nullable CommandExecutor commandExecutor);
```

with:

```java
List<AutomationDecision> decide(StoredEvent event);
```

Remove `CommandExecutor` from the public handler API. Update Javadocs and inline examples to show returned decisions rather than direct side effects.

### 3. Update `AutomationDispatcher`

Replace the current direct callback:

```java
handler.react(event, commandExecutor);
```

with:

```java
List<AutomationDecision> decisions = handler.decide(event);
for (AutomationDecision decision : decisions) {
    executeDecision(automationName, event, decision);
}
```

Decision execution should remain inside the existing correlation/causation scope so `ExecuteCommand` receives the same metadata behavior as current `react(...)` command execution.

Suggested dispatcher logic:

```java
private void executeDecision(
        String automationName,
        StoredEvent event,
        AutomationDecision decision) {

    switch (decision) {
        case AutomationDecision.ExecuteCommand c -> {
            if (commandExecutor == null) {
                throw new IllegalStateException(
                        "Automation " + automationName +
                        " produced ExecuteCommand, but no CommandExecutor is available");
            }
            commandExecutor.execute(c.command());
        }

        case AutomationDecision.NoOp n ->
                log.debug("Automation {} no-op for event position={}: {}",
                        automationName, event.position(), n.reason());
    }
}
```

### 4. Preserve Failure Semantics

Keep the current at-least-once behavior:

- If `decide(...)` throws, processing fails.
- If `commandExecutor.execute(...)` throws, processing fails.
- If one decision in a list fails, stop immediately and let the poller retry the event later.

Do not add per-decision retry or compensation in this change. The event poller already owns retry by not advancing progress after failure.

### 5. Define Empty-List Behavior

Allow:

```java
return List.of();
```

Treat it as an implicit no-op and count the event as processed.

For clearer domain code, recommend explicit no-op decisions:

```java
return List.of(new AutomationDecision.NoOp("already handled"));
```

### 6. Update Auto-Configuration

`AutomationsAutoConfiguration` can keep a single handler registry:

```java
Map<String, AutomationHandler>
```

Keep duplicate-name validation.

Keep requiring `CommandExecutor` if handlers are present, unless no-op-only automations become a supported use case. Since the only useful non-no-op decision in the initial hierarchy is `ExecuteCommand`, requiring a `CommandExecutor` at startup is reasonable.

### 7. Update Handlers And Tests

Every current handler implementation changes from:

```java
public void react(StoredEvent event, CommandExecutor commandExecutor) {
    commandExecutor.execute(command);
}
```

to:

```java
public List<AutomationDecision> decide(StoredEvent event) {
    return List.of(new AutomationDecision.ExecuteCommand(command));
}
```

Handler tests should become simpler.

Before:

```java
CommandExecutor executor = mock(CommandExecutor.class);

handler.react(event, executor);

verify(executor).execute(command);
```

After:

```java
assertThat(handler.decide(event))
        .containsExactly(new AutomationDecision.ExecuteCommand(command));
```

### 8. Update Documentation And Examples

Update references to:

```text
react(event, commandExecutor)
```

to:

```text
decide(event) -> List<AutomationDecision>
```

Primary files to check:

- `crablet-automations/README.md`
- root `README.md` automation references
- `docs/tutorials/05-automations.md`
- `docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial05AutomationsSample.java`
- `wallet-example-app/src/main/java/com/crablet/wallet/automations/WalletOpenedAutomation.java`
- architecture docs that say automations perform side effects directly

Documentation should state the boundary clearly:

- Automations are for in-process orchestration.
- Automations return decisions.
- `ExecuteCommand` is the supported way to cause domain changes.
- External publishing belongs to `crablet-outbox`.
- To publish externally from an automation flow: automation returns command, command appends event, outbox publishes event.

### 9. Add Focused Tests

Minimum useful coverage:

- `AutomationHandler` default method tests still pass.
- Dispatcher executes one `ExecuteCommand`.
- Dispatcher executes multiple decisions in order.
- Dispatcher treats `NoOp` as successful.
- Dispatcher treats an empty decision list as successful.
- Dispatcher propagates correlation/causation while executing command decisions.
- Dispatcher emits `AutomationExecutionErrorMetric` when `decide(...)` fails.
- Dispatcher emits `AutomationExecutionErrorMetric` when command execution fails.
- Integration test proves an automation decision can execute a command and append the resulting event.

### 10. Verify

Run targeted automation tests first:

```bash
./mvnw -pl crablet-automations test
```

Then run the broader reactor:

```bash
./mvnw test
```

## What To Avoid

Do not add open-ended decision variants yet:

- `PublishToOutbox`
- `HttpCall`
- `SendEmail`
- `PublishKafka`

Those variants would turn automations into a general side-effect engine and blur the module boundaries.

Do not use this shape:

```java
interface AutomationDecision {
    void execute(AutomationExecutionContext ctx);
}
```

That moves execution behavior back into the decision object and weakens the dispatcher's ability to own semantics, metrics, logging, and architectural boundaries.

## Expected Flow

The final model should be:

```text
StoredEvent
   -> AutomationHandler.decide(...)
   -> List<AutomationDecision>
   -> AutomationDispatcher executes decisions
   -> CommandExecutor executes commands
   -> CommandHandler returns CommandDecision
   -> EventStore appends events
   -> Outbox publishes stored events externally
```

That keeps responsibilities clear: automations describe orchestration, commands own domain writes, and outbox owns external publishing.
