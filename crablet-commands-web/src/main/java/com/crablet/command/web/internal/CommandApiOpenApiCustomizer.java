package com.crablet.command.web.internal;

import com.crablet.command.web.CommandApiProperties;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces the generic {@code POST /api/commands} request body schema with a {@code oneOf}
 * composed schema built from the exposed command classes, enabling Swagger UI to render
 * a per-command-type dropdown with the correct field forms.
 * <p>
 * Only registered when springdoc is on the classpath (see {@link CommandWebOpenApiAutoConfiguration}).
 */
class CommandApiOpenApiCustomizer implements OpenApiCustomizer {

    private final ExposedCommandTypeRegistry registry;
    private final String basePath;

    CommandApiOpenApiCustomizer(ExposedCommandTypeRegistry registry, CommandApiProperties properties) {
        this.registry = registry;
        this.basePath = properties.getBasePath();
    }

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Class<?>> exposed = registry.exposedCommandsByType();
        if (exposed.isEmpty() || openApi.getPaths() == null) {
            return;
        }

        var pathItem = openApi.getPaths().get(basePath);
        if (pathItem == null || pathItem.getPost() == null) {
            return;
        }

        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }

        // Register component schemas for each exposed command class
        var sortedEntries = exposed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        for (var entry : sortedEntries) {
            ModelConverters.getInstance()
                    .readAll(entry.getValue())
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
        }

        // Build oneOf + discriminator
        List<Schema> oneOfList = sortedEntries.stream()
                .map(e -> (Schema) new Schema<>().$ref("#/components/schemas/" + e.getValue().getSimpleName()))
                .toList();

        var mapping = new LinkedHashMap<String, String>();
        sortedEntries.forEach(e ->
                mapping.put(e.getKey(), "#/components/schemas/" + e.getValue().getSimpleName()));

        var requestBodySchema = new Schema<>()
                .oneOf(oneOfList)
                .discriminator(new Discriminator().propertyName("commandType").mapping(mapping));

        pathItem.getPost().requestBody(
                new RequestBody()
                        .required(true)
                        .content(new Content()
                                .addMediaType("application/json", new MediaType().schema(requestBodySchema))));
    }
}
