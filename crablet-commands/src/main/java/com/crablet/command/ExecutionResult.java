package com.crablet.command;

import org.jspecify.annotations.Nullable;

/**
 * Result of command execution indicating whether operation was idempotent.
 * <p>
 * {@link CommandExecutor} returns this after interpreting the handler's
 * {@link CommandDecision}:
 * <ul>
 *   <li>{@link CommandDecision.NoOp} or a duplicate idempotent command maps to an idempotent result</li>
 *   <li>A successful append maps to a created result</li>
 * </ul>
 * <p>
 * The reason field provides context for internal components (logging, monitoring, debugging).
 * REST clients only need to check wasCreated() to determine HTTP status code.
 */
public record ExecutionResult(boolean wasIdempotent, @Nullable String reason) {

    public static ExecutionResult created() {
        return new ExecutionResult(false, null);
    }

    public static ExecutionResult idempotent(String reason) {
        return new ExecutionResult(true, reason);
    }

    public boolean wasCreated() {
        return !wasIdempotent;
    }
}
