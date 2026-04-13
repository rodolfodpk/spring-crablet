package com.crablet.command.web;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit allowlist of command classes exposed through the generic REST command API.
 * <p>
 * This registry is separate from handler discovery: all available commands still come from the
 * discovered {@code CommandHandler<?>} beans, while this type declares which subset is reachable
 * over HTTP.
 * <p>
 * Declare a bean of this type in your application configuration to enable HTTP access:
 * <pre>{@code
 * @Bean
 * CommandApiExposedCommands commandApiExposedCommands() {
 *     return CommandApiExposedCommands.of(OpenWalletCommand.class, DepositCommand.class);
 * }
 * }</pre>
 */
public record CommandApiExposedCommands(Set<Class<?>> commandClasses) {

    public CommandApiExposedCommands {
        Objects.requireNonNull(commandClasses, "commandClasses must not be null");
        commandClasses = Set.copyOf(commandClasses);
    }

    public static CommandApiExposedCommands of(Class<?>... commandClasses) {
        Objects.requireNonNull(commandClasses, "commandClasses must not be null");
        Set<Class<?>> classes = Arrays.stream(commandClasses)
                .peek(commandClass -> Objects.requireNonNull(commandClass, "command class must not be null"))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return new CommandApiExposedCommands(classes);
    }
}
