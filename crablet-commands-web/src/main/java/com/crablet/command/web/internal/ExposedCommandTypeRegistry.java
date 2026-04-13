package com.crablet.command.web.internal;

import com.crablet.command.internal.DiscoveredCommandRegistry;
import com.crablet.command.web.CommandApiExposedCommands;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps the explicitly exposed command classes to command type identifiers.
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
        Map<String, Class<?>> exposedByType = exposedCommands.commandClasses().stream()
                .collect(Collectors.toMap(
                        discoveredCommands::commandTypeForClass,
                        commandClass -> commandClass,
                        (left, right) -> left));
        return new ExposedCommandTypeRegistry(exposedByType, discoveredCommands.commandTypes());
    }

    Class<?> resolve(String commandType) {
        return exposedCommandsByType.get(commandType);
    }

    boolean isKnown(String commandType) {
        return knownCommandTypes.contains(commandType);
    }
}
