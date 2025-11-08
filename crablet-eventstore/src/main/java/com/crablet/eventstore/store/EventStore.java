package com.crablet.eventstore.store;

import com.crablet.eventstore.dcb.AppendCondition;
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
     * AppendIf appends events to the store with explicit DCB concurrency control.
     * This method makes it clear when consistency/concurrency checks are required.
     * Use this for operations that need to ensure data hasn't changed since projection.
     * For simple appends without concurrency checks, use AppendCondition.empty().
     * 
     * @param events The events to append (must not be empty)
     * @param condition The append condition for concurrency control
     * @return The transaction ID of the transaction that appended the events (never null for successful append)
     * @throws IllegalArgumentException if the events list is empty
     * @throws ConcurrencyException if the append condition is violated
     */
    String appendIf(List<AppendEvent> events, AppendCondition condition);

    /**
     * Project projects state from events matching query with cursor.
     * Returns final aggregated states and append condition for DCB concurrency control
     * 
     * @param query The query to filter events
     * @param after Cursor to project events after (use Cursor.zero() for all events)
     * @param stateType The type of state to project
     * @param projectors List of projectors to apply to events
     */
    <T> ProjectionResult<T> project(
            Query query,
            Cursor after,
            Class<T> stateType,
            List<StateProjector<T>> projectors
    );

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
