package com.crablet.eventstore.store;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;

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
    default String appendCommutative(List<AppendEvent> events) {
        return appendIf(events, AppendCondition.empty());
    }

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
    default String appendNonCommutative(List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition) {
        return appendIf(events, AppendConditionBuilder.of(decisionModel, streamPosition).build());
    }

    /**
     * Appends idempotent events — entity creation; fails if an event with the same tag already exists
     * (e.g., OpenWallet, DefineCourse).
     *
     * @param events   The events to append (must not be empty)
     * @param eventType The event type name used for the idempotency check
     * @param tagKey   The tag key used for the idempotency check
     * @param tagValue The tag value used for the idempotency check
     * @return The transaction ID of the transaction that appended the events
     * @throws IllegalArgumentException if the events list is empty
     * @throws ConcurrencyException if a duplicate event with the same tag already exists
     */
    default String appendIdempotent(List<AppendEvent> events, String eventType, String tagKey, String tagValue) {
        return appendIf(events, AppendCondition.idempotent(eventType, tagKey, tagValue));
    }

    /**
     * Low-level escape hatch for appending events with an explicit DCB condition.
     * Prefer the three semantic overloads ({@link #appendCommutative}, {@link #appendNonCommutative},
     * {@link #appendIdempotent}) over this method.
     *
     * @param events The events to append (must not be empty)
     * @param condition The append condition for concurrency control
     * @return The transaction ID of the transaction that appended the events (never null for successful append)
     * @throws IllegalArgumentException if the events list is empty
     * @throws ConcurrencyException if the append condition is violated
     * @deprecated Prefer the semantic overloads: {@link #appendCommutative}, {@link #appendNonCommutative},
     *             {@link #appendIdempotent}.
     */
    @Deprecated
    String appendIf(List<AppendEvent> events, AppendCondition condition);

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
     *   <li>The transactionId returned by {@code appendIf()} represents the entire transaction</li>
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
    void storeCommand(String commandJson, String commandType, String transactionId);
}
