package com.crablet.core;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * EventStore is the core interface for appending and reading events.
 * This is the primary abstraction that users interact with.
 */
public interface EventStore {
    
    /**
     * Query reads events matching the query with optional cursor.
     * after == null: query from beginning of stream
     * after != null: query from specified cursor position
     */
    List<StoredEvent> query(Query query, Cursor after);
    
    /**
     * Append appends events to the store without any consistency/concurrency checks.
     * Use this only when there are no business rules or consistency requirements.
     * For operations that require DCB concurrency control, use appendIf instead.
     */
    void append(List<AppendEvent> events);
    
    /**
     * AppendIf appends events to the store with explicit DCB concurrency control.
     * This method makes it clear when consistency/concurrency checks are required.
     * Use this for operations that need to ensure data hasn't changed since projection.
     */
    void appendIf(List<AppendEvent> events, AppendCondition condition);
    
    /**
     * Project projects state from events matching projectors with optional cursor.
     * after == null: project from beginning of stream
     * after != null: project from specified cursor position
     * Returns final aggregated states and append condition for DCB concurrency control
     */
    <T> ProjectionResult<T> project(
        List<StateProjector<T>> projectors, 
        Cursor after,
        Class<T> stateType
    );
    
    /**
     * Convenience method for Map<String, Object> projections.
     */
    ProjectionResult<Map<String, Object>> project(
        List<StateProjector> projectors, 
        Cursor after
    );
    
    /**
     * Create a transaction-scoped EventStore that uses the provided connection.
     * All operations (queries, projections, appends) will use the same transaction.
     * 
     * Use this method when you need to execute multiple EventStore operations
     * within the same database transaction for consistency.
     */
    EventStore withConnection(Connection connection);
    
    /**
     * Execute operations within a single transaction.
     * EventStore manages connection lifecycle internally.
     * 
     * This is the preferred way to execute transactional operations as it
     * encapsulates all database connection management and ensures proper
     * transaction handling (commit on success, rollback on error).
     * 
     * @param operation Function that receives a transaction-scoped EventStore
     * @param <T> Type of result returned by the operation
     * @return Result of the operation
     * @throws RuntimeException if transaction fails
     */
    <T> T executeInTransaction(Function<EventStore, T> operation);
    
    /**
     * Store a command in the database for audit and query purposes.
     * This method stores command metadata alongside events for traceability.
     * 
     * @param command The command to store
     * @param transactionId The transaction ID associated with this command
     * @throws RuntimeException if storage fails
     */
    void storeCommand(Command command, String transactionId);
    
    /**
     * Get the current transaction ID from the database.
     * This is used to associate commands with their transaction context.
     * 
     * @return The current transaction ID as a string
     * @throws RuntimeException if retrieval fails
     */
    String getCurrentTransactionId();
}
