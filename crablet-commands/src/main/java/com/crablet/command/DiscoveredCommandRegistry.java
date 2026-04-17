package com.crablet.command;

import com.crablet.command.internal.CommandTypeResolver;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of command handlers discovered from the Spring application context.
 * <p>
 * Built once at startup from all {@link CommandHandler} beans and provides fast lookups between
 * command type strings, command classes, and their handlers. Immutable after construction.
 * <p>
 * Consumed internally by {@link CommandExecutor} for auto-discovery routing, and exposed publicly
 * so application code (e.g. HTTP adapters) can enumerate registered command types at runtime.
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

    /**
     * Build a registry from a list of command handlers.
     * <p>
     * Each handler's command type is derived via reflection from its generic type parameter.
     * Throws {@link InvalidCommandException} if two handlers map to the same command type.
     *
     * @param commandHandlers the handlers to register; must not contain duplicates for the same type
     * @return a new, immutable registry
     */
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

    /**
     * Returns all registered handlers keyed by their command type string.
     *
     * @return unmodifiable map from command type to handler
     */
    public Map<String, CommandHandler<?>> handlersByType() {
        return handlersByType;
    }

    /**
     * Returns all registered command type strings.
     *
     * @return unmodifiable set of command type names
     */
    public Set<String> commandTypes() {
        return commandClassesByType.keySet();
    }

    /**
     * Returns the command type string for the given command class.
     *
     * @param commandClass the command class to look up
     * @return the registered command type string
     * @throws IllegalStateException if no handler is registered for the class
     */
    public String commandTypeForClass(Class<?> commandClass) {
        String commandType = commandTypesByClass.get(commandClass);
        if (commandType == null) {
            throw new IllegalStateException("No discovered handler registered for command class: " + commandClass.getName());
        }
        return commandType;
    }

    /**
     * Returns the command class registered under the given type string, or {@code null} if unknown.
     *
     * @param commandType the command type string
     * @return the corresponding command class, or {@code null} if not registered
     */
    public Class<?> commandClassForType(String commandType) {
        return commandClassesByType.get(commandType);
    }
}
