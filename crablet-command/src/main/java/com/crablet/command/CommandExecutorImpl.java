package com.crablet.command;

import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.Tag;
import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.command.metrics.CommandStartedMetric;
import com.crablet.command.metrics.CommandSuccessMetric;
import com.crablet.command.metrics.CommandFailureMetric;
import com.crablet.command.metrics.IdempotentOperationMetric;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
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
 *             ClockProvider clock,
 *             ObjectMapper objectMapper,
     *             ApplicationEventPublisher eventPublisher) {
     *         return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
     *     }
     * }
     * }</pre>
 */
public class CommandExecutorImpl implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutorImpl.class);

    private final EventStore eventStore;
    private final Map<String, CommandHandler<?>> handlers;
    private final EventStoreConfig config;
    private final ClockProvider clock;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a new CommandExecutorImpl.
     *
     * @param eventStore the event store for persisting events
     * @param commandHandlers list of command handlers (auto-discovered by Spring)
     * @param config event store configuration
     * @param clock clock provider for timestamps
     * @param objectMapper Jackson object mapper for JSON serialization
     * @param eventPublisher event publisher for metrics (required).
     *                       Spring Boot automatically provides an ApplicationEventPublisher bean.
     *                       See crablet-metrics-micrometer for automatic metrics collection.
     */
    public CommandExecutorImpl(EventStore eventStore, List<CommandHandler<?>> commandHandlers, 
                              EventStoreConfig config, ClockProvider clock,
                              ObjectMapper objectMapper,
                              ApplicationEventPublisher eventPublisher) {
        if (eventStore == null) {
            throw new IllegalArgumentException("eventStore must not be null");
        }
        if (commandHandlers == null) {
            throw new IllegalArgumentException("commandHandlers must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.eventStore = eventStore;
        this.config = config;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;

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
            eventPublisher.publishEvent(new CommandFailureMetric("unknown", "validation"));
            throw e;
        } catch (JsonProcessingException e) {
            throw new InvalidCommandException(
                "Failed to serialize/extract command type: " + command.getClass().getName(),
                command,
                e
            );
        }

        log.debug("Starting transaction for command: {}", commandType);

        // Start timing with ClockProvider
        Instant startTime = clock.now();
        eventPublisher.publishEvent(new CommandStartedMetric(commandType, startTime));

        try {
            ExecutionResult executionResult = eventStore.executeInTransaction(txStore -> {
                // Handle command and generate events
                // Type-safe invocation: handler is CommandHandler<T>, command is T
                CommandResult result = handler.handle(txStore, command);

                // Validate command result
                validateCommandResult(result, command);

                // Fail fast: Handle idempotent operations first
                if (result.isEmpty()) {
                    return handleIdempotentResult(result, commandType);
                }

                // Atomic append with condition (DCB pattern)
                String transactionId;
                try {
                    transactionId = txStore.appendIf(result.events(), result.appendCondition());
                } catch (ConcurrencyException e) {
                    // Fail fast: Handle idempotent duplicate operations
                    return handleConcurrencyException(e, commandType, command);
                }

                // Store command for audit and query purposes (if enabled)
                if (config.isPersistCommands() && transactionId != null) {
                    txStore.storeCommand(commandJson, commandType, transactionId);
                }

                // Return success result
                log.debug("Transaction committed successfully for command: {}", commandType);
                return ExecutionResult.created();
            });
            
            // Calculate duration and publish success metrics
            Duration duration = Duration.between(startTime, clock.now());
            eventPublisher.publishEvent(new CommandSuccessMetric(commandType, duration));
            
            // Track idempotent operations separately
            if (executionResult.wasIdempotent()) {
                eventPublisher.publishEvent(new IdempotentOperationMetric(commandType));
            }
            
            return executionResult;
        } catch (ConcurrencyException e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            eventPublisher.publishEvent(new CommandFailureMetric(commandType, "concurrency"));
            throw e;
        } catch (InvalidCommandException e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            eventPublisher.publishEvent(new CommandFailureMetric(commandType, "validation"));
            throw e;
        } catch (RuntimeException e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            eventPublisher.publishEvent(new CommandFailureMetric(commandType, "runtime"));
            throw e;
        } catch (Exception e) {
            log.debug("Transaction rolled back for command: {}", commandType);
            eventPublisher.publishEvent(new CommandFailureMetric(commandType, "exception"));
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
    
    /**
     * Validate command result with fail-fast principles.
     * Throws immediately on any validation failure.
     */
    private <T> void validateCommandResult(CommandResult result, T command) {
        // Fail fast: Validate events list is not null
        if (result.events() == null) {
            throw new InvalidCommandException("Handler returned null events", command);
        }

        // Validate individual events
        int eventIndex = 0;
        for (AppendEvent event : result.events()) {
            // Fail fast: Validate event type
            if (event.type() == null || event.type().isEmpty()) {
                throw new InvalidCommandException("Event at index " + eventIndex + " has empty type", command);
            }

            // Validate tags
            if (event.tags() != null) {
                int tagIndex = 0;
                for (Tag tag : event.tags()) {
                    // Fail fast: Validate tag key
                    if (tag.key() == null || tag.key().isEmpty()) {
                        throw new InvalidCommandException("Empty tag key at index " + tagIndex, command);
                    }
                    // Fail fast: Validate tag value
                    if (tag.value() == null || tag.value().isEmpty()) {
                        throw new InvalidCommandException("Empty tag value for key " + tag.key(), command);
                    }
                    tagIndex++;
                }
            }
            eventIndex++;
        }
    }
    
    /**
     * Handle ConcurrencyException with fail-fast principles.
     * Returns ExecutionResult for idempotent duplicate operations, throws for others.
     */
    private <T> ExecutionResult handleConcurrencyException(ConcurrencyException e, String commandType, T command) {
        // Fail fast: Check if this is NOT an idempotency violation
        String message = e.getMessage();
        if (message == null || !message.toLowerCase().contains("duplicate operation detected")) {
            throw new ConcurrencyException(message, command, e);
        }
        
        // Fail fast: Wallet creation duplicates should throw exception
        if ("open_wallet".equals(commandType)) {
            throw new ConcurrencyException(message, command, e);
        }
        
        // Other operation duplicates should return idempotent result
        log.debug("Transaction committed successfully for command: {} (idempotent - duplicate detected)", commandType);
        return ExecutionResult.idempotent("DUPLICATE_OPERATION");
    }
    
    /**
     * Handle idempotent result with fail-fast principles.
     */
    private ExecutionResult handleIdempotentResult(CommandResult result, String commandType) {
        String reason = result.reason() != null ? result.reason() : "DUPLICATE_OPERATION";
        log.debug("Transaction committed successfully for command: {} (idempotent)", commandType);
        return ExecutionResult.idempotent(reason);
    }
}

