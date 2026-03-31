package com.crablet.eventstore.dcb;

import org.jspecify.annotations.Nullable;

/**
 * Exception thrown when AppendCondition fails due to concurrent modification.
 * This is equivalent to go-crablet's concurrency violation handling.
 * 
 * See also:
 * - EventStoreException - for infrastructure failures
 * - InvalidCommandException - for validation errors
 */
public class ConcurrencyException extends RuntimeException {

    /** The command that triggered the violation, for diagnostics. May be {@code null} when thrown from low-level infrastructure. */
    public final @Nullable Object command;

    /** Structured details about the DCB violation (error code, matching event count). May be {@code null} for legacy throw sites. */
    public final @Nullable DCBViolation violation;

    /** Thrown from infrastructure with a message only — no command context available. */
    public ConcurrencyException(@Nullable String message) {
        super(message);
        this.command = null;
        this.violation = null;
    }

    /** Thrown when the command is known but no structured violation details are available. */
    public ConcurrencyException(@Nullable String message, @Nullable Object command) {
        super(message);
        this.command = command;
        this.violation = null;
    }

    /** Thrown when wrapping an underlying cause (e.g., a database exception). */
    public ConcurrencyException(@Nullable String message, @Nullable Object command, @Nullable Throwable cause) {
        super(message, cause);
        this.command = command;
        this.violation = null;
    }

    /** Thrown with full diagnostic context — command and structured DCB violation details. */
    public ConcurrencyException(@Nullable String message, @Nullable Object command, @Nullable DCBViolation violation) {
        super(message);
        this.command = command;
        this.violation = violation;
    }

    /** Thrown when the command is not available but structured violation details are. */
    public ConcurrencyException(@Nullable String message, @Nullable DCBViolation violation) {
        super(message);
        this.command = null;
        this.violation = violation;
    }
}
