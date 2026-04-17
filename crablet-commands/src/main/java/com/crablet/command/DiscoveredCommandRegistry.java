package com.crablet.command;

import com.crablet.command.internal.CommandTypeResolver;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of command handlers discovered from the Spring application context.
 * Provides mappings between command types (string keys), command classes, and their handlers.
 */
public final class DiscoveredCommandRegistry {

    private final Map<String, CommandHandler<?>> handlersByType;
    private final Map<String, Class<?>> commandClassesByType;
    private final Map<Class<?>, String> commandTypesByClass;

    private DiscoveredCommandRegistry(
            Map<String, CommandHandler<?>> handlersByType,
            Map<String, Class<?>> commandClassesByType,
            Map<Class<?>, String> commandTypesByClass) {
        this.handlersByType = Map.copyOf(handlersByType);
        this.commandClassesByType = Map.copyOf(commandClassesByType);
        this.commandTypesByClass = Map.copyOf(commandTypesByClass);
    }

    public static DiscoveredCommandRegistry fromHandlers(List<CommandHandler<?>> commandHandlers) {
        Map<String, CommandHandler<?>> handlersByType = commandHandlers.stream()
                .collect(Collectors.toMap(
                        handler -> CommandTypeResolver.extractCommandTypeFromHandler(handler.getClass()),
                        handler -> handler,
                        (h1, h2) -> {
                            String type = CommandTypeResolver.extractCommandTypeFromHandler(h1.getClass());
                            throw new InvalidCommandException("Duplicate handler for command type: " + type, type);
                        }
                ));

        Map<String, Class<?>> commandClassesByType = commandHandlers.stream()
                .collect(Collectors.toMap(
                        handler -> CommandTypeResolver.extractCommandTypeFromHandler(handler.getClass()),
                        handler -> CommandTypeResolver.extractCommandClassFromHandler(handler.getClass()),
                        (c1, c2) -> c1
                ));

        Map<Class<?>, String> commandTypesByClass = commandClassesByType.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        return new DiscoveredCommandRegistry(handlersByType, commandClassesByType, commandTypesByClass);
    }

    public Map<String, CommandHandler<?>> handlersByType() {
        return handlersByType;
    }

    public Set<String> commandTypes() {
        return commandClassesByType.keySet();
    }

    public String commandTypeForClass(Class<?> commandClass) {
        String commandType = commandTypesByClass.get(commandClass);
        if (commandType == null) {
            throw new IllegalStateException("No discovered handler registered for command class: " + commandClass.getName());
        }
        return commandType;
    }

    public Class<?> commandClassForType(String commandType) {
        return commandClassesByType.get(commandType);
    }
}
