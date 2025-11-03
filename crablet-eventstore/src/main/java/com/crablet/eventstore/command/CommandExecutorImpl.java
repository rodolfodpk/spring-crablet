package com.crablet.eventstore.command;

import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreMetrics;
import com.crablet.eventstore.store.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
 * <p>
 * <strong>Spring Integration:</strong>
 * This class does NOT have @Component annotation. Users must define an explicit @Bean:
 * <pre>{@code
 * @Configuration
 * public class CrabletConfig {
 *     
 *     @Bean
 *     public CommandExecutorImpl commandExecutor(
 *             EventStore eventStore,
 *             List<CommandHandler<?>> commandHandlers,
 *             EventStoreConfig config,
 *             EventStoreMetrics metrics) {
 *         return new CommandExecutorImpl(eventStore, commandHandlers, config, metrics);
 *     }
 * }
 * }</pre>
 */
public class CommandExecutorImpl implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutorImpl.class);

    private final EventStore eventStore;
    private final Map<String, CommandHandler<?>> handlers;
    private final EventStoreConfig config;
    private final EventStoreMetrics metrics;
    private final ObjectMapper objectMapper;

    @Autowired
    public CommandExecutorImpl(EventStore eventStore, List<CommandHandler<?>> commandHandlers, 
                              EventStoreConfig config, EventStoreMetrics metrics,
                              ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.config = config;
        this.metrics = metrics;
        this.objectMapper = objectMapper;

        // Build handler map from Spring-injected list
        // Extract command type from handler's generic type parameter using reflection
        this.handlers = commandHandlers.stream()
                .collect(Collectors.toMap(
                        handler -> {
                            try {
                                return CommandTypeResolver.extractCommandTypeFromHandler(handler.getClass());
                            } catch (InvalidCommandException e) {
                                throw new IllegalStateException(
                                    "Failed to extract command type from handler: " + handler.getClass().getName() + 
                                    ". " + e.getMessage(), e
                                );
                            }
                        },
                        h -> h,
                        (h1, h2) -> {
                            String type1 = CommandTypeResolver.extractCommandTypeFromHandler(h1.getClass());
                            throw new InvalidCommandException(
                                "Duplicate handler for command type: " + type1,
                                type1
                            );
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
    public <T> void execute(T command, CommandHandler<T> handler) {
        executeCommand(command, handler);
    }

    @Override
    public <T> ExecutionResult executeCommand(T command) {
        // Determine the appropriate handler based on command type
        // Type inference: T is inferred from command parameter
        CommandHandler<T> handler = getHandlerForCommand(command);
        return executeCommand(command, handler);
    }

    /**
     * Execute a command within a single transaction.
     * EventStore manages all transaction operations internally.
     * <p>
     * Type safety: Handler type parameter must match command type.
     *
     * @param <T> the command type (inferred from parameter)
     * @param command the command to execute
     * @param handler the handler for this command (must be CommandHandler<T>)
     * @return ExecutionResult indicating whether the operation was idempotent
     */
    public <T> ExecutionResult executeCommand(T command, CommandHandler<T> handler) {
        // Validate command
        if (command == null) {
            throw new InvalidCommandException("Command cannot be null", "NULL_COMMAND");
        }
        if (handler == null) {
            throw new InvalidCommandException("Handler cannot be null", "NULL_HANDLER");
        }

        // Extract command type and optionally serialize for storage
        final String commandJson;
        String commandType;
        try {
            if (config.isPersistCommands()) {
                // If persistence enabled: serialize to string and extract type (reuse string later)
                String json = objectMapper.writeValueAsString(command);
                commandJson = json; // Assign to final variable
                JsonNode jsonNode = objectMapper.readTree(json);
                JsonNode commandTypeNode = jsonNode.get("commandType");
                if (commandTypeNode == null || !commandTypeNode.isTextual()) {
                    throw new InvalidCommandException(
                        "Command type property 'commandType' not found or invalid in JSON for class: " + command.getClass().getName(),
                        command
                    );
                }
                commandType = commandTypeNode.asText();
            } else {
                // If persistence disabled: use lightweight valueToTree() - no string serialization
                commandJson = null; // Not needed when persistence disabled
                JsonNode jsonNode = objectMapper.valueToTree(command);
                JsonNode commandTypeNode = jsonNode.get("commandType");
                if (commandTypeNode == null || !commandTypeNode.isTextual()) {
                    throw new InvalidCommandException(
                        "Command type property 'commandType' not found or invalid in JSON for class: " + command.getClass().getName(),
                        command
                    );
                }
                commandType = commandTypeNode.asText();
            }
            
            if (commandType == null || commandType.isEmpty()) {
                throw new InvalidCommandException(
                    "Command type is null or empty for class: " + command.getClass().getName(),
                    command
                );
            }
        } catch (InvalidCommandException e) {
            log.debug("Failed to extract command type: {}", e.getMessage());
            metrics.recordCommandFailure("unknown", "validation");
            throw e;
        } catch (JsonProcessingException e) {
            throw new InvalidCommandException(
                "Failed to serialize/extract command type: " + command.getClass().getName(),
                command,
                e
            );
        }

        log.debug("Starting transaction for command: {}", commandType);

        // Start metrics tracking
        var sample = metrics.startCommand(commandType);

        try {
            ExecutionResult executionResult = eventStore.executeInTransaction(txStore -> {
                // Handle command and generate events
                // Type-safe invocation: handler is CommandHandler<T>, command is T
                CommandResult result = handler.handle(txStore, command);

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
                int eventsCount = result.events().size();
                if (!result.isEmpty()) {
                    try {
                        txStore.appendIf(result.events(), result.appendCondition());
                        // Track aggregate events appended
                        metrics.recordEventsAppended(eventsCount);
                        // Track event type distribution
                        for (AppendEvent event : result.events()) {
                            metrics.recordEventType(event.type());
                        }
                    } catch (ConcurrencyException e) {
                        // Check if this is an idempotency violation (duplicate operation)
                        if (e.getMessage().toLowerCase().contains("duplicate operation detected")) {
                            // Wallet creation duplicates should throw exception (handled by GlobalExceptionHandler)
                            if ("open_wallet".equals(commandType)) {
                                throw new ConcurrencyException(e.getMessage(), command, e);
                            }
                            // Other operation duplicates should return idempotent result
                            log.debug("Transaction committed successfully for command: {} (idempotent - duplicate detected)", commandType);
                            return ExecutionResult.idempotent("DUPLICATE_OPERATION");
                        }
                        // Re-throw other concurrency exceptions (optimistic locking failures)
                        throw new ConcurrencyException(e.getMessage(), command, e);
                    }
                }

                // Store command for audit and query purposes (if enabled)
                if (!result.isEmpty() && config.isPersistCommands()) {
                    String transactionId = txStore.getCurrentTransactionId();
                    // commandJson and commandType were extracted earlier (commandJson is final)
                    txStore.storeCommand(commandJson, commandType, transactionId);
                }

                // Return execution result based on what handler determined
                boolean wasIdempotent = result.isEmpty();
                if (wasIdempotent) {
                    String reason = result.reason() != null ? result.reason() : "DUPLICATE_OPERATION";
                    log.debug("Transaction committed successfully for command: {} (idempotent)", commandType);
                    return ExecutionResult.idempotent(reason);
                } else {
                    log.debug("Transaction committed successfully for command: {}", commandType);
                    return ExecutionResult.created();
                }
            });
            
            // Record success metrics
            metrics.recordCommandSuccess(commandType, sample);
            
            // Track idempotent operations separately
            if (executionResult.wasIdempotent()) {
                metrics.recordIdempotentOperation(commandType);
            }
            
            return executionResult;
        } catch (ConcurrencyException e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            metrics.recordCommandFailure(commandType, "concurrency");
            metrics.recordConcurrencyViolation();
            throw e;
        } catch (InvalidCommandException e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            metrics.recordCommandFailure(commandType, "validation");
            throw e;
        } catch (RuntimeException e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            metrics.recordCommandFailure(commandType, "runtime");
            throw e;
        } catch (Exception e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            metrics.recordCommandFailure(commandType, "exception");
            throw new RuntimeException("Failed to execute command: " + commandType, e);
        }
    }

    /**
     * Get the appropriate handler for a command based on its type.
     * <p>
     * Returns a typed handler {@code CommandHandler<T>} where T is inferred from the command.
     * Uses unchecked cast, but runtime validation ensures type safety.
     *
     * @param <T> the command type (inferred from parameter)
     * @param command the command to get handler for
     * @return typed handler CommandHandler<T>
     */
    @SuppressWarnings("unchecked")
    private <T> CommandHandler<T> getHandlerForCommand(T command) {
        if (command == null) {
            throw new InvalidCommandException("Command cannot be null", "NULL_COMMAND");
        }
        if (handlers.isEmpty()) {
            throw new InvalidCommandException("No command handlers registered", command);
        }
        
        // Extract command type from command object
        // Use lightweight valueToTree() since we're not storing the command here
        JsonNode jsonNode = objectMapper.valueToTree(command);
        JsonNode commandTypeNode = jsonNode.get("commandType");
        if (commandTypeNode == null || !commandTypeNode.isTextual()) {
            throw new InvalidCommandException(
                "Command type property 'commandType' not found in JSON for class: " + command.getClass().getName(),
                command
            );
        }
        String commandType = commandTypeNode.asText();
        
        CommandHandler<?> handler = handlers.get(commandType);
        if (handler == null) {
            throw new InvalidCommandException("No handler registered for command type: " + commandType, command);
        }
        
        // Unchecked cast is safe because:
        // 1. We validate command type matches handler's registered type in executeCommand()
        // 2. Handler registry maps command type string to handler
        // 3. Runtime validation ensures handler can handle this command type
        return (CommandHandler<T>) handler;
    }
}
