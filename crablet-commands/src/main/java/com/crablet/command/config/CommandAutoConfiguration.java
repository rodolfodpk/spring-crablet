package com.crablet.command.config;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutors;
import com.crablet.command.CommandHandler;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.config.EventStoreAutoConfiguration;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for the Crablet Command framework.
 * <p>
 * Activates automatically when an {@link EventStore} bean is present (provided by
 * {@link EventStoreAutoConfiguration} or declared manually).
 * <p>
 * Registers a {@link CommandExecutor} that auto-discovers all {@link CommandHandler} beans
 * in the application context. Handlers annotated with {@code @Component} are picked up automatically.
 * <p>
 * <strong>Overriding:</strong> declare your own {@link CommandExecutor} bean to bypass
 * this auto-configuration.
 */
@AutoConfiguration(after = EventStoreAutoConfiguration.class)
public class CommandAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommandExecutor commandExecutor(
            EventStore eventStore,
            List<CommandHandler<?>> commandHandlers,
            EventStoreConfig config,
            ClockProvider clock,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        return CommandExecutors.create(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }
}
