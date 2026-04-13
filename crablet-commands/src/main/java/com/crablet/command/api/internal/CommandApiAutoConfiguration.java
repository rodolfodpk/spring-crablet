package com.crablet.command.api.internal;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandHandler;
import com.crablet.command.api.CommandApiExposedCommands;
import com.crablet.command.api.CommandApiProperties;
import com.crablet.command.config.CommandAutoConfiguration;
import com.crablet.command.internal.DiscoveredCommandRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.web.servlet.function.RequestPredicates.POST;
import static org.springframework.web.servlet.function.RouterFunctions.route;

/**
 * Auto-configuration for the generic REST command API.
 */
@AutoConfiguration(after = CommandAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RouterFunction.class)
@ConditionalOnProperty(prefix = "crablet.commands.api", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CommandApiProperties.class)
public class CommandApiAutoConfiguration {

    @Bean
    @ConditionalOnBean(CommandExecutor.class)
    public DiscoveredCommandRegistry discoveredCommandRegistry(List<CommandHandler<?>> commandHandlers) {
        return DiscoveredCommandRegistry.fromHandlers(commandHandlers);
    }

    @Bean
    @ConditionalOnBean({CommandExecutor.class, DiscoveredCommandRegistry.class})
    public ExposedCommandTypeRegistry exposedCommandTypeRegistry(
            DiscoveredCommandRegistry discoveredCommands,
            ObjectProvider<CommandApiExposedCommands> exposedCommandsProvider) {
        CommandApiExposedCommands exposedCommands = exposedCommandsProvider.getIfAvailable();
        if (exposedCommands == null) {
            throw new IllegalStateException(
                    "crablet.commands.api.enabled=true requires a CommandApiExposedCommands bean");
        }
        return ExposedCommandTypeRegistry.from(discoveredCommands, exposedCommands);
    }

    @Bean
    @ConditionalOnBean({CommandExecutor.class, ExposedCommandTypeRegistry.class})
    public HttpCommandApiHandler commandApiHandler(
            CommandExecutor commandExecutor,
            ExposedCommandTypeRegistry exposedCommands,
            ObjectMapper objectMapper) {
        return new HttpCommandApiHandler(commandExecutor, exposedCommands, objectMapper);
    }

    @Bean
    @ConditionalOnBean(HttpCommandApiHandler.class)
    public RouterFunction<ServerResponse> commandApiRouter(
            HttpCommandApiHandler commandApiHandler,
            CommandApiProperties properties) {
        return route(POST(properties.getBasePath()), commandApiHandler::executeCommand);
    }
}
