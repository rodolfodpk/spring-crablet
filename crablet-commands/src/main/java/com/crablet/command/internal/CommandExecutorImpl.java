package com.crablet.command.internal;

import com.crablet.command.CommandDecision;
import com.crablet.command.CommandExecutor;
import com.crablet.command.DiscoveredCommandRegistry;
import com.crablet.command.OnDuplicate;
import com.crablet.command.CommandHandler;
import com.crablet.command.ExecutionResult;
import com.crablet.command.InvalidCommandException;
import com.crablet.command.metrics.CommandFailureMetric;
import com.crablet.command.metrics.CommandStartedMetric;
import com.crablet.command.metrics.CommandSuccessMetric;
import com.crablet.command.metrics.IdempotentOperationMetric;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.CommandAuditStore;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.CorrelationContext;
import com.crablet.eventstore.DCBViolation;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.Tag;
import com.crablet.eventstore.query.StateProjector;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
 * This class does NOT have {@code @Component} annotation. Prefer wiring
 * {@link com.crablet.command.CommandExecutors#create(EventStore, List, EventStoreConfig, ClockProvider, ObjectMapper, ApplicationEventPublisher)}
 * from application configuration instead of constructing this internal type directly.
 * For example:
 * <pre>{@code
 * @Configuration
 * public class CrabletConfig {
 *
 *     @Bean
 *     public CommandExecutor commandExecutor(
 *             EventStore eventStore,
 *             List<CommandHandler<?>> commandHandlers,
 *             EventStoreConfig config,
 *             ClockProvider clock,
 *             ObjectMapper objectMapper,
 *             ApplicationEventPublisher eventPublisher) {
 *         return CommandExecutors.create(
 *                 eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
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

        this.handlers = DiscoveredCommandRegistry.fromHandlers(commandHandlers).handlersByType();

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
    public <T> ExecutionResult execute(T command) {
        CommandHandler<T> handler = getHandlerForCommand(command);
        return execute(command, handler);
    }

    @Override
    public <T> ExecutionResult execute(T command, @Nullable UUID correlationId) {
        if (correlationId == null) {
            return execute(command);
        }
        try {
            return ScopedValue.where(CorrelationContext.CORRELATION_ID, correlationId)
                              .call(() -> execute(command));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unexpected checked exception during command execution", e);
        }
    }

    @Override
    public <T> ExecutionResult execute(T command, CommandHandler<T> handler) {
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
        } catch (JacksonException e) {
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

        AtomicReference<String> operationType = new AtomicReference<>("unknown");

        try {
            ExecutionResult executionResult = eventStore.executeInTransaction(txStore -> {
                // Handle command and generate events
                // Type-safe invocation: handler is CommandHandler<T>, command is T
                CommandDecision result = handler.handle(txStore, command);

                // Validate command result
                validateCommandDecision(result, command);

                // Idempotent re-execution: handler signals no events to append
                if (result instanceof CommandDecision.NoOp e) {
                    operationType.set("no_op");
                    return handleIdempotentResult(e.reason(), commandType);
                }

                // Append events using the appropriate Crablet semantic method
                @Nullable String transactionId;
                try {
                    transactionId = switch (result) {
                        case CommandDecision.Commutative c -> {
                            operationType.set("commutative");
                            yield txStore.appendCommutative(c.events());
                        }
                        case CommandDecision.CommutativeGuarded cg -> {
                            operationType.set("commutative_guarded");
                            // Selective lifecycle guard: detect if entity state changed (e.g., WalletClosed)
                            // between the handler's projection and this append, without blocking
                            // concurrent commutative operations (e.g., DepositMade not in guard query).
                            if (txStore.project(cg.guardQuery(), cg.guardPosition(), StateProjector.exists()).state()) {
                                throw new ConcurrencyException(
                                    "Commutative guard violated: lifecycle state changed since projection",
                                    new DCBViolation(
                                        "GUARD_VIOLATION", "Concurrent lifecycle event detected", 1));
                            }
                            yield txStore.appendCommutative(cg.events());
                        }
                        case CommandDecision.NonCommutative nc -> {
                            operationType.set("non_commutative");
                            yield txStore.appendNonCommutative(nc.events(), nc.decisionModel(), nc.streamPosition());
                        }
                        case CommandDecision.Idempotent i -> {
                            operationType.set("idempotent");
                            yield txStore.appendIdempotent(i.events(), i.eventType(), i.tagKey(), i.tagValue());
                        }
                        case CommandDecision.NoOp e ->
                            throw new IllegalStateException("unreachable: empty case handled above");
                    };
                } catch (ConcurrencyException e) {
                    // Dispatch based on the domain's declared duplicate policy
                    return handleConcurrencyException(e, commandType, command, result);
                }

                // Store command for audit and query purposes (if enabled)
                if (config.isPersistCommands() && commandJson != null && transactionId != null
                        && txStore instanceof CommandAuditStore auditStore) {
                    auditStore.storeCommand(commandJson, commandType, transactionId);
                }

                // Return success result
                log.debug("Transaction committed successfully for command: {}", commandType);
                return ExecutionResult.created();
            });

            // Calculate duration and publish success metrics
            Duration duration = Duration.between(startTime, clock.now());
            eventPublisher.publishEvent(new CommandSuccessMetric(commandType, duration, operationType.get()));

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
        // 1. We validate command type matches handler's registered type in execute()
        // 2. Handler registry maps command type string to handler
        // 3. Runtime validation ensures handler can handle this command type
        return (CommandHandler<T>) handler;
    }

    /**
     * Validate command result with fail-fast principles.
     * Throws immediately on any validation failure.
     */
    private <T> void validateCommandDecision(CommandDecision result, T command) {
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
     * Handle ConcurrencyException by consulting the domain's declared {@link OnDuplicate} policy.
     * Returns ExecutionResult for idempotent duplicate operations, throws for others.
     */
    private <T> ExecutionResult handleConcurrencyException(ConcurrencyException e, String commandType, T command,
                                                           CommandDecision result) {
        String message = e.getMessage();
        if (message == null || !message.toLowerCase().contains("duplicate operation detected")) {
            throw new ConcurrencyException(message, command, e);
        }

        // Respect the domain's declared policy: THROW or RETURN_IDEMPOTENT
        if (result instanceof CommandDecision.Idempotent i && i.onDuplicate() == OnDuplicate.THROW) {
            throw new ConcurrencyException(message, command, e);
        }

        log.debug("Transaction committed successfully for command: {} (idempotent - duplicate detected)", commandType);
        return ExecutionResult.idempotent("DUPLICATE_OPERATION");
    }

    /**
     * Handle idempotent result with fail-fast principles.
     */
    private ExecutionResult handleIdempotentResult(String reason, String commandType) {
        String r = reason != null ? reason : "DUPLICATE_OPERATION";
        log.debug("Transaction committed successfully for command: {} (idempotent)", commandType);
        return ExecutionResult.idempotent(r);
    }
}
