package com.crablet.eventstore;

import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * EventStore is the core interface for appending and reading events.
 * This is the primary abstraction that users interact with.
 */
public interface EventStore {

    /**
     * Appends commutative events — order-independent operations where no conflict detection is needed
     * (e.g., deposits, credits, batch increments).
     *
     * @param events The events to append (must not be empty)
     * @return The transaction ID of the transaction that appended the events
     * @throws IllegalArgumentException if the events list is empty
     */
    String appendCommutative(List<AppendEvent> events);

    /**
     * Appends non-commutative events — order-dependent operations with stream-position-based DCB conflict check
     * (e.g., withdrawals, transfers, capacity changes).
     *
     * @param events         The events to append (must not be empty)
     * @param decisionModel  The query used for the DCB consistency check
     * @param streamPosition The stream position captured from the projection result
     * @return The transaction ID of the transaction that appended the events
     * @throws IllegalArgumentException if the events list is empty
     * @throws ConcurrencyException if a concurrent modification is detected
     */
    String appendNonCommutative(List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition);

    /**
     * Appends idempotent events — entity creation; fails if an event with the same tag already exists
     * (e.g., OpenWallet, DefineCourse).
     *
     * @param events    The events to append (must not be empty)
     * @param eventType The event type name used for the idempotency check
     * @param tagKey    The tag key used for the idempotency check
     * @param tagValue  The tag value used for the idempotency check
     * @return The transaction ID of the transaction that appended the events
     * @throws IllegalArgumentException if the events list is empty
     * @throws ConcurrencyException if a duplicate event with the same tag already exists
     */
    String appendIdempotent(List<AppendEvent> events, String eventType, String tagKey, String tagValue);

    /**
     * Project projects state from events matching query with stream position.
     * Returns final aggregated states and append condition for DCB concurrency control
     *
     * @param query The query to filter events
     * @param after StreamPosition to project events after (use StreamPosition.zero() for all events)
     * @param stateType The type of state to project
     * @param projectors List of projectors to apply to events
     */
    <T> ProjectionResult<T> project(
            Query query,
            StreamPosition after,
            Class<T> stateType,
            List<StateProjector<T>> projectors
    );

    /**
     * Convenience overload for single-projector use. Eliminates the class token and List.of() boilerplate.
     *
     * @param query      The query to filter events
     * @param after      StreamPosition to project events after (use StreamPosition.zero() for all events)
     * @param projector  The single projector to apply
     */
    @SuppressWarnings("unchecked")
    default <T> ProjectionResult<T> project(Query query, StreamPosition after, StateProjector<T> projector) {
        return project(query, after, (Class<T>) Object.class, List.of(projector));
    }

    /**
     * Convenience overload that projects from {@link StreamPosition#zero()} with a single projector.
     * Use this for the common case where you want to project the full event history.
     *
     * @param query     The query to filter events
     * @param projector The single projector to apply
     */
    default <T> ProjectionResult<T> project(Query query, StateProjector<T> projector) {
        return project(query, StreamPosition.zero(), projector);
    }

    /**
     * Convenience overload that projects from {@link StreamPosition#zero()} with multiple projectors.
     * Use this when projecting several independent state slices from the same event stream in one pass.
     *
     * @param query      The query to filter events
     * @param projectors The projectors to apply
     */
    @SuppressWarnings("unchecked")
    default <T> ProjectionResult<T> project(Query query, List<StateProjector<T>> projectors) {
        return project(query, StreamPosition.zero(), (Class<T>) Object.class, projectors);
    }

    /**
     * Returns {@code true} if at least one event matching {@code query} exists.
     * <p>
     * The production implementation uses {@code SELECT EXISTS(SELECT 1 FROM events WHERE ...)}
     * so PostgreSQL short-circuits at the first match — no event data is fetched or deserialized.
     * The {@code default} implementation falls back to {@code project()} for custom implementors.
     *
     * @param query the query defining which events to check
     * @return {@code true} if any matching event exists, {@code false} otherwise
     */
    default boolean exists(Query query) {
        return project(query, StreamPosition.zero(), StateProjector.exists()).state();
    }

    /**
     * Returns {@code true} if any event matching {@code query} appeared <em>after</em> {@code after}.
     * <p>
     * Used by {@link com.crablet.command.internal.CommandExecutorImpl} to enforce a selective DCB guard
     * on commutative operations: the guard checks only lifecycle events (e.g., WalletOpened, WalletClosed),
     * not the full decision model, so concurrent commutative operations (e.g., deposits) are still allowed
     * while lifecycle changes (e.g., wallet closing) are detected.
     * <p>
     * The {@code default} implementation uses {@code project()} for compatibility with custom implementors.
     * The production implementation uses {@code SELECT EXISTS(...)} for efficiency.
     *
     * @param query the query defining which events to check for conflicts
     * @param after the stream position after which to look for events
     * @return {@code true} if a conflicting event was found, {@code false} otherwise
     */
    default boolean hasConflict(Query query, StreamPosition after) {
        return project(query, after, StateProjector.exists()).state();
    }

    /**
     * Execute operations within a single transaction.
     * EventStore manages connection lifecycle internally.
     * <p>
     * This is the preferred way to execute transactional operations as it
     * encapsulates all database connection management and ensures proper
     * transaction handling (commit on success, rollback on error).
     * <p>
     * <strong>Transaction Guarantees:</strong>
     * <ul>
     *   <li>All operations (queries, projections, appends, command storage) use the same database transaction</li>
     *   <li>All operations see a consistent database snapshot</li>
     *   <li>All operations commit atomically, or all rollback on error</li>
     *   <li>The transactionId returned by append methods represents the entire transaction</li>
     * </ul>
     *
     * @param operation Function that receives a transaction-scoped EventStore
     * @param <T>       Type of result returned by the operation
     * @return Result of the operation
     * @throws RuntimeException if transaction fails
     */
    <T> T executeInTransaction(Function<EventStore, T> operation);

    /**
     * Store a command in the database for audit and query purposes.
     * This method stores command metadata alongside events for traceability.
     *
     * @param commandJson   The command serialized as JSON string
     * @param commandType   The command type string
     * @param transactionId The transaction ID associated with this command
     * @throws RuntimeException if storage fails
     */
    void storeCommand(String commandJson, @Nullable String commandType, String transactionId);
}
