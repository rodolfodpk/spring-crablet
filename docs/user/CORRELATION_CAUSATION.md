# Correlation and Causation IDs

Crablet records two optional identifiers on every appended event:

| Field | Type | Purpose |
|-------|------|---------|
| `correlationId` | `UUID` (nullable) | Ties all events produced by the same business operation (e.g. one HTTP request) |
| `causationId` | `Long` (nullable) | The `position` of the event that directly triggered the current operation in an automation chain |

Both are stored as nullable columns on the `crablet_events` table (`correlation_id UUID`, `causation_id BIGINT`) and are readable via `StoredEvent.correlationId()` and `StoredEvent.causationId()`.

## How It Works

`CorrelationContext` (in `crablet-eventstore`) holds the active IDs for the current call stack using Java's `ScopedValue` — the virtual-thread-safe alternative to `ThreadLocal`. Values are immutable within a scope and garbage-collected automatically when the scope exits.

```java
// Read anywhere in the call stack (returns null when unbound):
UUID cid  = CorrelationContext.correlationId();
Long caus = CorrelationContext.causationId();
```

The `EventStoreImpl` append layer reads these values automatically. No changes to command handlers or event definitions are needed.

## Module Responsibilities

| Module | Responsibility |
|--------|----------------|
| `crablet-eventstore` | Defines `CorrelationContext`, persists IDs on append, and exposes them through `StoredEvent` |
| `crablet-commands` | Binds an explicit correlation ID for `CommandExecutor.execute(command, correlationId)` |
| `crablet-commands-web` | Reads/generates the HTTP correlation header and passes it to `CommandExecutor` |
| `crablet-automations` | Propagates the triggering event's correlation ID and sets causation to the triggering event position |
| `crablet-views` | Binds the projected event's correlation ID and position while projector code runs |
| `crablet-outbox` | Delivers `StoredEvent` instances to publishers; publishers can forward IDs to external systems |
| `crablet-db-migrations` | Provides the shared Flyway migration that adds `correlation_id` and `causation_id` |

## Propagation Flow

A typical HTTP-triggered automation chain looks like this:

1. `crablet-commands-web` receives `POST /api/commands`, reads `X-Correlation-Id`, or generates a new UUID.
2. `crablet-commands` runs the command inside a `CorrelationContext.CORRELATION_ID` scope.
3. `crablet-eventstore` appends the command's event with that correlation ID and a `null` causation ID.
4. `crablet-event-poller` fetches the stored event, including its correlation and causation columns.
5. `crablet-automations` handles the event, keeps the same correlation ID, and sets causation to the triggering event's position.
6. If the automation executes another command, `crablet-eventstore` appends the new event with the same correlation ID and causation set.
7. `crablet-views` and `crablet-outbox` receive `StoredEvent` values containing both IDs, so projectors and publishers can keep the trace intact.

## Explicit API (programmatic callers)

For batch jobs, internal services, or tests that want to set the correlation ID without a servlet filter, pass it directly to `CommandExecutor`:

```java
UUID correlationId = UUID.randomUUID(); // or derive from an upstream system
commandExecutor.execute(command, correlationId);
```

Pass `null` when no correlation context is available — behaviour is identical to `execute(command)` and events will have a `null` `correlationId`.

This is the recommended approach when there is no HTTP request in scope. The method binds `CorrelationContext.CORRELATION_ID` as a `ScopedValue` for the duration of the execution and clears it automatically when the call returns.

## HTTP Layer

If you use `crablet-commands-web`, the generic command endpoint handles this for you. The built-in filter reads the configured correlation header, defaults to `X-Correlation-Id`, generates a fresh UUID when the header is absent, echoes the final value in the response, and passes it into `CommandExecutor`.

For application-specific controllers that append events outside the generic command API, declare a servlet filter that binds `CorrelationContext.CORRELATION_ID` for the duration of the request. The wallet example app ships one such implementation:

```java
// examples/wallet-example-app/src/main/java/com/crablet/wallet/api/CorrelationFilter.java
@Component
public class CorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("X-Correlation-ID");
        UUID correlationId = (header != null && !header.isBlank())
            ? UUID.fromString(header)
            : UUID.randomUUID();

        response.setHeader("X-Correlation-ID", correlationId.toString());

        ScopedValue.where(CorrelationContext.CORRELATION_ID, correlationId)
                   .call(() -> { filterChain.doFilter(request, response); return null; });
    }
}
```

- If the caller sends `X-Correlation-ID: <uuid>`, that value is used.
- If the header is absent or not a valid UUID, a fresh UUID is generated.
- The final ID is echoed back in the response header so callers can trace their request through logs and the `crablet_events` table.

Copy this filter into your application and annotate it with `@Component` — Spring Boot picks it up automatically.

## Automation Chains

`AutomationDispatcher` propagates correlation and sets causation automatically for each event it processes. No additional configuration is needed when automations return `AutomationDecision.ExecuteCommand` or `AutomationDecision.NoOp` from `decide(...)`:

```java
// Internally, for each event the automation handles:
var scope = ScopedValue.where(CorrelationContext.CAUSATION_ID, event.position());
if (event.correlationId() != null) {
    scope = scope.where(CorrelationContext.CORRELATION_ID, event.correlationId());
}
scope.run(() -> dispatcher.execute(handler.decide(event)));
```

This means:

- All events produced by an automation handler share the same `correlationId` as the triggering event.
- The `causationId` of each produced event is set to the `position` of the event that triggered the automation.
- Chains (automation A triggers command → event → automation B) propagate correlation end-to-end with accurate causation at every step.

This guarantee applies to work executed inside the dispatcher-owned scope. Prefer returning
`ExecuteCommand` decisions for internal follow-up behavior; the dispatcher executes those commands
synchronously while the triggering event's IDs are still bound. If an automation starts a new
thread, submits work to a custom executor, schedules work for later, or appends events directly
after `decide(...)` returns, the application owns correlation/causation propagation for that work.
Use outbox for external asynchronous publication.

## View Projectors

`AbstractViewProjector` binds correlation and causation while each stored event is projected:

- `CorrelationContext.correlationId()` is the projected event's `correlationId`, or `null`.
- `CorrelationContext.causationId()` is the projected event's `position`.

Most projectors only update read-model tables and do not need to read the context. If a projector performs additional application work that appends events, those events inherit the projected event as their cause.

## Outbox Publishers

Outbox publishers receive `StoredEvent` instances, so they can read the values directly:

```java
UUID correlationId = event.correlationId();
Long causationId = event.causationId();
```

The built-in `LogPublisher` includes both values in its event log lines. Custom publishers should include them in outbound metadata or payloads when downstream systems need traceability.

## Reading the Values

```java
// In a view projector, outbox publisher, or anywhere you receive a StoredEvent:
UUID correlationId = storedEvent.correlationId(); // null if not set
Long causationId   = storedEvent.causationId();   // null if not set
```

Both are nullable. Events appended before the feature was introduced, or through paths where no `ScopedValue` scope is active, will have `null` for both fields.

## Database Columns

The framework eventstore migration includes these columns from the start. It lives in
`crablet-db-migrations` for runtime apps and is mirrored in `crablet-test-support`
for module integration tests:

| Location | File | Applied to |
|----------|------|-----------|
| `crablet-db-migrations/src/main/resources/db/migration/` | `V1__crablet_eventstore_schema.sql` | Example apps and apps that include the shared migration artifact |
| `crablet-test-support/src/main/resources/db/migration/` | `V1__crablet_eventstore_schema.sql` | All test databases |

If you maintain your own schema instead of using `crablet-db-migrations`, include:

```sql
ALTER TABLE crablet_events
    ADD COLUMN IF NOT EXISTS correlation_id UUID,
    ADD COLUMN IF NOT EXISTS causation_id   BIGINT;
```
