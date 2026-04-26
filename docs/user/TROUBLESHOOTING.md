# Troubleshooting

## Build Issues

**Problem:** "Cannot find symbol: shared-examples-domain classes"
- **Solution:** Run `make install` or manually build in order: `crablet-eventstore` → `shared-examples-domain` → reactor modules

**Problem:** Tests fail with "Docker not running"
- **Solution:** Integration tests use Testcontainers. Either start Docker or run unit tests only: `./mvnw test -DexcludedGroups=integration`

## DCB Pattern Issues

**Problem:** `ConcurrencyException` thrown when no concurrent modifications occurred
- **Enhanced Diagnostics (v1.0+):** The exception now includes diagnostic hints and matching event counts
- **Solution:** Check that your decision model query matches the tags on your events. Mismatched tags cause false conflicts.
- **Debug:** Enable debug logging (`logging.level.com.crablet.eventstore=DEBUG`) to see DCB check details

**Problem:** Idempotency check not working (duplicate events stored)
- **Enhanced Diagnostics (v1.0+):** Exception message now includes specific hint about idempotency tag usage
- **Solution:** Ensure you're using `AppendCondition.idempotent()` with the correct event type and tag. The tag must uniquely identify the operation (e.g., `deposit_id`, not just `wallet_id`).
- **Debug:** Check exception message for `Hint:` section with guidance

**Problem:** Query returns no events but events exist in database
- **Solution:** Verify tags on events match query criteria. Common issue: forgetting period tags (`year`, `month`) when using closing the books pattern.
- **Debug:** Enable debug logging to see exact query parameters being sent to PostgreSQL

## Testing Issues

**Problem:** Unit test projections return wrong state
- **Solution:** Ensure you seed events with `.tag()` calls matching your query patterns. `InMemoryEventStore` filters by tags just like the real implementation.

**Problem:** `ClassCastException` in tests
- **Solution:** Use pattern matching with sealed interfaces instead of casts. If event isn't sealed, extend `AbstractViewProjector` instead of `AbstractTypedViewProjector`.

**Problem:** Integration test database pollution between tests
- **Solution:** Use unique IDs: `String walletId = "wallet-" + System.currentTimeMillis();` or `UUID.randomUUID().toString()`

## View Projection Issues

**Problem:** View not updating (events not processed)
- **Solution:** Check leader election — only one instance processes. Verify with logs: "Leader election acquired for view: {viewName}"

**Problem:** View falls behind (lag increasing)
- **Solution:** Check batch size, processing time, and error logs. Consider increasing batch size or optimizing projector logic.

**Problem:** View projector throws exceptions but silently fails
- **Enhanced Diagnostics (v1.0+):** Error logs now include full event context (type, position, transaction_id, tags)
- **Solution:** Views swallow exceptions by design (at-least-once processing). Check logs for enhanced error details including the exact event that failed.
- **Debug:** Error message now includes hint to check if projector handles all event types

## Event Deserialization Issues

**Problem:** `JsonMappingException` when deserializing events
- **Solution:** Ensure event classes are properly configured for Jackson (records work out of the box). Check that field names match JSON.

**Problem:** Events stored but projector can't read them
- **Solution:** Verify `getEventTypes()` in projector returns correct event type names. Use `EventType.type(Class)` pattern for consistency.
