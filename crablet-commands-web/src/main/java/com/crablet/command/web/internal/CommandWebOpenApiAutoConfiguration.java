package com.crablet.command.web.internal;

import com.crablet.command.web.CommandApiProperties;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for springdoc/OpenAPI integration.
 * <p>
 * Activated only when {@code springdoc-openapi-starter-webmvc-ui} is on the classpath.
 * Registers a customizer that enriches the {@code POST /api/commands} operation with a
 * {@code oneOf} schema built from the currently exposed command classes.
 */
@AutoConfiguration(after = CommandWebAutoConfiguration.class)
@ConditionalOnClass(OpenApiCustomizer.class)
class CommandWebOpenApiAutoConfiguration {

    @Bean
    CommandApiOpenApiCustomizer commandApiOpenApiCustomizer(
            ExposedCommandTypeRegistry exposedCommandTypeRegistry,
            CommandApiProperties commandApiProperties) {
        return new CommandApiOpenApiCustomizer(exposedCommandTypeRegistry, commandApiProperties);
    }
}
