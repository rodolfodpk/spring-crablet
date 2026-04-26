# Correlation and Causation IDs

Crablet records two optional identifiers on every appended event:

| Field | Type | Purpose |
|-------|------|---------|
| `correlationId` | `UUID` (nullable) | Ties all events produced by the same business operation (e.g. one HTTP request) |
| `causationId` | `Long` (nullable) | The `position` of the event that directly triggered the current operation in an automation chain |

Both are stored as nullable columns on the `events` table (`correlation_id UUID`, `causation_id BIGINT`) and are readable via `StoredEvent.correlationId()` and `StoredEvent.causationId()`.

## How It Works

`CorrelationContext` (in `crablet-eventstore`) holds the active IDs for the current call stack using Java's `ScopedValue` — the virtual-thread-safe alternative to `ThreadLocal`. Values are immutable within a scope and garbage-collected automatically when the scope exits.

```java
// Read anywhere in the call stack (returns null when unbound):
UUID cid  = CorrelationContext.correlationId();
Long caus = CorrelationContext.causationId();
```

The `EventStoreImpl` append layer reads these values automatically. No changes to command handlers or event definitions are needed.

## Explicit API (programmatic callers)

For batch jobs, internal services, or tests that want to set the correlation ID without a servlet filter, pass it directly to `CommandExecutor`:

```java
UUID correlationId = UUID.randomUUID(); // or derive from an upstream system
commandExecutor.execute(command, correlationId);
```

Pass `null` when no correlation context is available — behaviour is identical to `execute(command)` and events will have a `null` `correlationId`.

This is the recommended approach when there is no HTTP request in scope. The method binds `CorrelationContext.CORRELATION_ID` as a `ScopedValue` for the duration of the execution and clears it automatically when the call returns.

## HTTP Layer

To attach a correlation ID to every HTTP request, declare a servlet filter that binds `CorrelationContext.CORRELATION_ID` for the duration of the request. The wallet example app ships a ready-to-use implementation:

```java
// wallet-example-app/src/main/java/com/crablet/wallet/api/CorrelationFilter.java
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
- The final ID is echoed back in the response header so callers can trace their request through logs and the `events` table.

Copy this filter into your application and annotate it with `@Component` — Spring Boot picks it up automatically.

## Automation Chains

`AutomationDispatcher` propagates correlation and sets causation automatically for each event it processes. No additional configuration is needed:

```java
// Internally, for each event the automation handles:
ScopedValue.where(CorrelationContext.CORRELATION_ID, event.correlationId())
           .where(CorrelationContext.CAUSATION_ID,   event.position())
           .run(() -> dispatcher.execute(handler.decide(event)));
```

This means:

- All events produced by an automation handler share the same `correlationId` as the triggering event.
- The `causationId` of each produced event is set to the `position` of the event that triggered the automation.
- Chains (automation A triggers command → event → automation B) propagate correlation end-to-end with accurate causation at every step.

## Reading the Values

```java
// In a view projector, outbox publisher, or anywhere you receive a StoredEvent:
UUID correlationId = storedEvent.correlationId(); // null if not set
Long causationId   = storedEvent.causationId();   // null if not set
```

Both are nullable. Events appended before the feature was introduced, or through paths where no `ScopedValue` scope is active, will have `null` for both fields.

## Database Columns

The migration that adds the columns is split between the two migration locations:

| Location | File | Applied to |
|----------|------|-----------|
| `crablet-test-support/src/main/resources/db/migration/` | `V5__correlation_causation.sql` | All test databases |
| `wallet-example-app/src/main/resources/db/migration/` | `V12__correlation_causation.sql` | The wallet example app |

For your own application, add a Flyway migration with:

```sql
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS correlation_id UUID,
    ADD COLUMN IF NOT EXISTS causation_id   BIGINT;
```
