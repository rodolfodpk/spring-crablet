package com.crablet.command;

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
     * <p>
     * Type inference: The command type {@code T} is automatically inferred from the command parameter.
     * Example: {@code executeCommand(walletCommand)} infers {@code T = WalletCommand}
     *
     * @param <T> the command type (inferred from parameter)
     * @param command the command to execute
     * @return ExecutionResult indicating whether the operation was idempotent
     */
    <T> ExecutionResult executeCommand(T command);
    
    /**
     * Execute a command within a single transaction using a specific handler.
     * All EventStore operations (queries, projections, appends) will use the same transaction.
     * <p>
     * Type safety: The handler type parameter must match the command type.
     * Example: {@code execute(walletCommand, walletHandler)} ensures types match at compile time.
     *
     * @param <T> the command type (inferred from parameter)
     * @param command the command to execute
     * @param handler the handler to use for this command (must be CommandHandler<T>)
     */
    <T> void execute(T command, CommandHandler<T> handler);
}

