package com.crablet.eventstore.command;

/**
 * Interface for executing commands and generating events within a single transaction.
 * <p>
 * CommandExecutor handles command validation, event generation, and transaction management.
 * It delegates to appropriate CommandHandlers based on command type.
 */
public interface CommandExecutor {
    
    /**
     * Execute a command within a single transaction.
     * All EventStore operations (queries, projections, appends) will use the same transaction.
     *
     * @param command the command to execute
     * @return ExecutionResult indicating whether the operation was idempotent
     */
    ExecutionResult executeCommand(Command command);
    
    /**
     * Execute a command within a single transaction using a specific handler.
     * All EventStore operations (queries, projections, appends) will use the same transaction.
     *
     * @param command the command to execute
     * @param handler the handler to use for this command
     */
    void execute(Command command, CommandHandler<?> handler);
}