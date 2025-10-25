package com.crablet.eventstore.impl;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.Command;
import com.crablet.eventstore.CommandExecutor;
import com.crablet.eventstore.CommandHandler;
import com.crablet.eventstore.CommandResult;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.ExecutionResult;
import com.crablet.eventstore.InvalidCommandException;
import com.crablet.eventstore.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of CommandExecutor.
 * Executes commands and generates events within a single transaction.
 * <p>
 * EventStore manages all database operations and transactions internally.
 * CommandExecutor focuses solely on command execution logic.
 * <p>
 * This is based on the Go implementation's CommandExecutor pattern.
 */
@Component
@ConditionalOnBean(CommandHandler.class)
public class CommandExecutorImpl implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutorImpl.class);

    private final EventStore eventStore;
    private final Map<String, CommandHandler<?>> handlers;
    private final EventStoreConfig config;

    @Autowired
    public CommandExecutorImpl(EventStore eventStore, List<CommandHandler<?>> commandHandlers, EventStoreConfig config) {
        this.eventStore = eventStore;
        this.config = config;

        // Build handler map from Spring-injected list
        this.handlers = commandHandlers.stream()
                .collect(Collectors.toMap(
                        CommandHandler::getCommandType,
                        h -> h,
                        (h1, _) -> {
                            throw new InvalidCommandException(
                                    "Duplicate handler for command type: " + h1.getCommandType(), h1.getCommandType());
                        }
                ));

        // Log EventStore configuration at startup
        log.info("EventStore - Command persistence: {}", config.isPersistCommands() ? "ENABLED" : "DISABLED");
        log.info("EventStore - Transaction isolation: {}", config.getTransactionIsolation());
        
        // Log handler registration
        if (handlers.isEmpty()) {
            log.warn("No command handlers registered - CommandExecutor will not be functional");
        } else {
            log.info("CommandExecutor registered with {} command handlers", handlers.size());
        }
    }

    @Override
    public void execute(Command command, CommandHandler<?> handler) {
        executeCommand(command, handler);
    }

    @Override
    public ExecutionResult executeCommand(Command command) {
        // Determine the appropriate handler based on command type
        CommandHandler<?> handler = getHandlerForCommand(command);
        return executeCommand(command, handler);
    }

    /**
     * Execute a command within a single transaction.
     * EventStore manages all transaction operations internally.
     *
     * @return ExecutionResult indicating whether the operation was idempotent
     */
    public ExecutionResult executeCommand(Command command, CommandHandler<?> handler) {
        // Validate command
        if (command == null) {
            throw new InvalidCommandException("Command cannot be null", "NULL_COMMAND");
        }
        if (handler == null) {
            throw new InvalidCommandException("Handler cannot be null", "NULL_HANDLER");
        }

        log.debug("Starting transaction for command: {}", command.getCommandType());

        try {
            return eventStore.executeInTransaction(txStore -> {
                // Handle command and generate events
                @SuppressWarnings("unchecked")
                CommandHandler<Command> typedHandler = (CommandHandler<Command>) handler;
                CommandResult result = typedHandler.handle(txStore, command);

                // Validate generated events - allow empty list for idempotent operations
                if (result.events() == null) {
                    throw new InvalidCommandException("Handler returned null events", command);
                }

                // Validate individual events with enhanced for-loops
                int eventIndex = 0;
                for (AppendEvent event : result.events()) {
                    if (event.type() == null || event.type().isEmpty()) {
                        throw new InvalidCommandException("Event at index " + eventIndex + " has empty type", command);
                    }

                    // Validate tags
                    if (event.tags() != null) {
                        int tagIndex = 0;
                        for (Tag tag : event.tags()) {
                            if (tag.key() == null || tag.key().isEmpty()) {
                                throw new InvalidCommandException("Empty tag key at index " + tagIndex, command);
                            }
                            if (tag.value() == null || tag.value().isEmpty()) {
                                throw new InvalidCommandException("Empty tag value for key " + tag.key(), command);
                            }
                            tagIndex++;
                        }
                    }
                    eventIndex++;
                }

                // Atomic append with condition (DCB pattern)
                if (!result.isEmpty()) {
                    try {
                        txStore.appendIf(result.events(), result.appendCondition());
                    } catch (ConcurrencyException e) {
                        // Check if this is an idempotency violation (duplicate operation)
                        if (e.getMessage().toLowerCase().contains("duplicate operation detected")) {
                            // Wallet creation duplicates should throw exception (handled by GlobalExceptionHandler)
                            if ("open_wallet".equals(command.getCommandType())) {
                                throw new ConcurrencyException(e.getMessage(), command, e);
                            }
                            // Other operation duplicates should return idempotent result
                            log.debug("Transaction committed successfully for command: {} (idempotent - duplicate detected)", command.getCommandType());
                            return ExecutionResult.idempotent("DUPLICATE_OPERATION");
                        }
                        // Re-throw other concurrency exceptions (optimistic locking failures)
                        throw new ConcurrencyException(e.getMessage(), command, e);
                    }
                }

                // Store command for audit and query purposes (if enabled)
                if (!result.isEmpty() && config.isPersistCommands()) {
                    String transactionId = txStore.getCurrentTransactionId();
                    txStore.storeCommand(command, transactionId);
                }

                // Return execution result based on what handler determined
                boolean wasIdempotent = result.isEmpty();
                if (wasIdempotent) {
                    String reason = result.reason() != null ? result.reason() : "DUPLICATE_OPERATION";
                    log.debug("Transaction committed successfully for command: {} (idempotent)", command.getCommandType());
                    return ExecutionResult.idempotent(reason);
                } else {
                    log.debug("Transaction committed successfully for command: {}", command.getCommandType());
                    return ExecutionResult.created();
                }
            });
        } catch (ConcurrencyException e) {
            log.debug("Transaction rolled back for command: {}", command.getCommandType());
            throw e;
        } catch (RuntimeException e) {
            log.debug("Transaction rolled back for command: {}", command.getCommandType());
            throw e;
        } catch (Exception e) {
            log.debug("Transaction rolled back for command: {}", command.getCommandType());
            throw new RuntimeException("Failed to execute command: " + command.getCommandType(), e);
        }
    }

    /**
     * Get the appropriate handler for a command based on its type.
     */
    private CommandHandler<?> getHandlerForCommand(Command command) {
        if (command == null) {
            throw new InvalidCommandException("Command cannot be null", "NULL_COMMAND");
        }
        if (handlers.isEmpty()) {
            throw new InvalidCommandException("No command handlers registered", command);
        }
        CommandHandler<?> handler = handlers.get(command.getCommandType());
        if (handler == null) {
            throw new InvalidCommandException("No handler registered for command type: " + command.getCommandType(), command);
        }
        return handler;
    }
}
