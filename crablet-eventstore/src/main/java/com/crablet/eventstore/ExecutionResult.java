package com.crablet.eventstore;

/**
 * Result of command execution indicating whether operation was idempotent.
 * <p>
 * CommandExecutor observes the CommandResult from handlers:
 * - If handler returns CommandResult.empty(), operation was idempotent
 * - If handler returns events, operation created new state
 * <p>
 * The reason field provides context for internal components (logging, monitoring, debugging).
 * REST clients only need to check wasCreated() to determine HTTP status code.
 */
public record ExecutionResult(boolean wasIdempotent, String reason) {

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

