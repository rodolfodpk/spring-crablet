package com.crablet.command.web.internal;

import com.crablet.command.DiscoveredCommandRegistry;
import com.crablet.command.web.CommandApiExposedCommands;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps the explicitly exposed command classes to command type identifiers.
 * Built once at startup; immutable thereafter.
 */
final class ExposedCommandTypeRegistry {

    private final Map<String, Class<?>> exposedCommandsByType;
    private final Set<String> knownCommandTypes;

    private ExposedCommandTypeRegistry(Map<String, Class<?>> exposedCommandsByType, Set<String> knownCommandTypes) {
        this.exposedCommandsByType = Map.copyOf(exposedCommandsByType);
        this.knownCommandTypes = Set.copyOf(knownCommandTypes);
    }

    static ExposedCommandTypeRegistry from(
            DiscoveredCommandRegistry discoveredCommands,
            CommandApiExposedCommands exposedCommands) {
        Map<String, Class<?>> exposedByType = discoveredCommands.commandTypes().stream()
                .filter(type -> {
                    Class<?> cls = discoveredCommands.commandClassForType(type);
                    return cls != null && exposedCommands.test(cls);
                })
                .collect(Collectors.toMap(
                        type -> type,
                        type -> Objects.requireNonNull(discoveredCommands.commandClassForType(type))));
        return new ExposedCommandTypeRegistry(exposedByType, discoveredCommands.commandTypes());
    }

    @Nullable Class<?> resolve(String commandType) {
        return exposedCommandsByType.get(commandType);
    }

    boolean isKnown(String commandType) {
        return knownCommandTypes.contains(commandType);
    }

    Map<String, Class<?>> exposedCommandsByType() {
        return exposedCommandsByType;
    }
}
