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
     */
    void appendIf(List<AppendEvent> events, AppendCondition condition);

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

    /**
     * Get the current transaction ID from the database.
     * This is used to associate commands with their transaction context.
     *
     * @return The current transaction ID as a string
     * @throws RuntimeException if retrieval fails
     */
    String getCurrentTransactionId();
}
