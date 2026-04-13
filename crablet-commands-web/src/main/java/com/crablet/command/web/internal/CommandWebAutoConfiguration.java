package com.crablet.command.web.internal;

import com.crablet.command.CommandExecutor;
import com.crablet.command.config.CommandAutoConfiguration;
import com.crablet.command.internal.DiscoveredCommandRegistry;
import com.crablet.command.CommandHandler;
import com.crablet.command.web.CommandApiExposedCommands;
import com.crablet.command.web.CommandApiProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Auto-configuration for the generic REST command API.
 * <p>
 * Activated automatically when {@code crablet-commands-web} is on the classpath.
 * Requires a {@link CommandApiExposedCommands} bean declaring which commands are
 * reachable over HTTP — Spring will fail at startup with a clear missing-bean message
 * if this bean is absent.
 * <p>
 * Configure the endpoint path via:
 * <pre>
 * crablet.commands.api.base-path=/api/commands
 * </pre>
 */
@AutoConfiguration(after = CommandAutoConfiguration.class)
@EnableConfigurationProperties(CommandApiProperties.class)
public class CommandWebAutoConfiguration {

    @Bean
    public DiscoveredCommandRegistry discoveredCommandRegistry(List<CommandHandler<?>> commandHandlers) {
        return DiscoveredCommandRegistry.fromHandlers(commandHandlers);
    }

    @Bean
    public ExposedCommandTypeRegistry exposedCommandTypeRegistry(
            DiscoveredCommandRegistry discoveredCommands,
            CommandApiExposedCommands exposedCommands) {
        return ExposedCommandTypeRegistry.from(discoveredCommands, exposedCommands);
    }

    @Bean
    public CommandApiRestController commandApiRestController(
            CommandExecutor commandExecutor,
            ExposedCommandTypeRegistry exposedCommands,
            ObjectMapper objectMapper) {
        return new CommandApiRestController(commandExecutor, exposedCommands, objectMapper);
    }

    @Bean
    public CommandApiExceptionHandler commandApiExceptionHandler() {
        return new CommandApiExceptionHandler();
    }
}
