package com.crablet.command;

import com.crablet.command.internal.CommandExecutorImpl;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Public factory for creating {@link CommandExecutor} instances.
 * <p>
 * Use this class from application configuration instead of instantiating
 * {@code com.crablet.command.internal.CommandExecutorImpl} directly.
 */
public final class CommandExecutors {

    private CommandExecutors() {
    }

    /**
     * Create the default {@link CommandExecutor} implementation.
     *
     * @param eventStore the event store for projections and appends
     * @param commandHandlers registered command handlers
     * @param config event store configuration
     * @param clock clock provider for timestamps
     * @param objectMapper mapper used to serialize commands and extract command types
     * @param eventPublisher publisher used for command metrics
     * @return a fully configured {@link CommandExecutor}
     */
    public static CommandExecutor create(
            EventStore eventStore,
            List<CommandHandler<?>> commandHandlers,
            EventStoreConfig config,
            ClockProvider clock,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }
}
