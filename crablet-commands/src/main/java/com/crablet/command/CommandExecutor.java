package com.crablet.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Interface for executing commands and generating events within a single transaction.
 * <p>
 * CommandExecutor handles command validation, event generation, and transaction management.
 * It delegates to appropriate CommandHandlers based on command type.
 */
public interface CommandExecutor {

    /**
     * Execute a command within a single transaction.
     * The handler is discovered automatically from the Spring context.
     * <p>
     * Type inference: The command type {@code T} is automatically inferred from the command parameter.
     * Example: {@code execute(walletCommand)} infers {@code T = WalletCommand}
     *
     * @param <T> the command type (inferred from parameter)
     * @param command the command to execute
     * @return ExecutionResult indicating whether the operation was new or idempotent
     */
    <T> ExecutionResult execute(T command);

    /**
     * Execute a command within a single transaction, binding an explicit correlation ID.
     * <p>
     * The {@code correlationId} is stored on every event appended during this execution and
     * can be read back via {@code StoredEvent.correlationId()}. Pass {@code null} when no
     * correlation context is available (e.g. background batch jobs) — the behaviour is
     * identical to calling {@link #execute(Object)}.
     * <p>
     * Use this overload for programmatic callers (batch jobs, internal services, tests) that
     * want explicit control over the correlation ID without relying on a servlet filter or
     * any ambient {@code ScopedValue} scope.
     *
     * @param <T>           the command type (inferred from parameter)
     * @param command       the command to execute
     * @param correlationId the correlation ID to attach to all appended events, or {@code null}
     * @return ExecutionResult indicating whether the operation was new or idempotent
     */
    <T> ExecutionResult execute(T command, @Nullable UUID correlationId);

    /**
     * Execute a command within a single transaction using an explicit handler.
     * Use this when you need to supply the handler directly instead of relying on auto-discovery.
     * <p>
     * Type safety: The handler type parameter must match the command type.
     * Example: {@code execute(walletCommand, walletHandler)} ensures types match at compile time.
     *
     * @param <T> the command type (inferred from parameter)
     * @param command the command to execute
     * @param handler the handler to use for this command (must be {@code CommandHandler<T>})
     * @return ExecutionResult indicating whether the operation was new or idempotent
     */
    <T> ExecutionResult execute(T command, CommandHandler<T> handler);
}
