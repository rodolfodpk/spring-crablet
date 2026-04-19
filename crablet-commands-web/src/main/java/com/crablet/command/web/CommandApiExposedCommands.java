package com.crablet.command.web;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Explicit allowlist of command classes exposed through the generic REST command API.
 * <p>
 * This registry is separate from handler discovery: all available commands still come from the
 * discovered {@code CommandHandler<?>} beans, while this type declares which subset is reachable
 * over HTTP.
 * <p>
 * Declare a bean of this type in your application configuration to enable HTTP access.
 * <p>
 * <b>Package-based exposure</b> (recommended for vertical slice layouts):
 * <pre>{@code
 * @Bean
 * CommandApiExposedCommands commandApiExposedCommands() {
 *     return CommandApiExposedCommands.fromPackages("com.myapp.wallet", "com.myapp.account");
 * }
 * }</pre>
 * <p>
 * <b>Explicit class list</b> (fine-grained control):
 * <pre>{@code
 * @Bean
 * CommandApiExposedCommands commandApiExposedCommands() {
 *     return CommandApiExposedCommands.of(OpenWalletCommand.class, DepositCommand.class);
 * }
 * }</pre>
 */
public final class CommandApiExposedCommands {

    private final Predicate<Class<?>> filter;

    private CommandApiExposedCommands(Predicate<Class<?>> filter) {
        this.filter = filter;
    }

    /**
     * Expose an explicit set of command classes.
     *
     * @param commandClasses the command classes to expose; must not be null or contain nulls
     * @return a new instance allowing exactly these classes
     */
    public static CommandApiExposedCommands of(Class<?>... commandClasses) {
        Objects.requireNonNull(commandClasses, "commandClasses must not be null");
        Set<Class<?>> classes = Arrays.stream(commandClasses)
                .peek(c -> Objects.requireNonNull(c, "command class must not be null"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new CommandApiExposedCommands(classes::contains);
    }

    /**
     * Expose all discovered commands whose class lives in one of the given packages (or a subpackage).
     * <p>
     * Matching is a simple {@code startsWith} on the fully-qualified package name — no classpath
     * scanning, evaluated once at startup against the already-discovered handler set.
     *
     * @param packages base package names; e.g. {@code "com.myapp.wallet"}
     * @return a new instance that exposes every command whose package starts with any of the given prefixes
     */
    public static CommandApiExposedCommands fromPackages(String... packages) {
        Objects.requireNonNull(packages, "packages must not be null");
        var pkgList = Arrays.stream(packages)
                .peek(p -> Objects.requireNonNull(p, "package must not be null"))
                .toList();
        return new CommandApiExposedCommands(clazz -> {
            String pkg = clazz.getPackageName();
            return pkgList.stream().anyMatch(pkg::startsWith);
        });
    }

    /**
     * Returns {@code true} if the given command class should be reachable over HTTP.
     */
    public boolean test(Class<?> commandClass) {
        return filter.test(commandClass);
    }
}
