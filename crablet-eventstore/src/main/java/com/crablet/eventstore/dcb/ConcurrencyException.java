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
    public final @Nullable Object command;
    public final @Nullable DCBViolation violation;

    public ConcurrencyException(@Nullable String message) {
        super(message);
        this.command = null;
        this.violation = null;
    }

    public ConcurrencyException(@Nullable String message, @Nullable Object command) {
        super(message);
        this.command = command;
        this.violation = null;
    }

    public ConcurrencyException(@Nullable String message, @Nullable Object command, @Nullable Throwable cause) {
        super(message, cause);
        this.command = command;
        this.violation = null;
    }

    public ConcurrencyException(@Nullable String message, @Nullable Object command, @Nullable DCBViolation violation) {
        super(message);
        this.command = command;
        this.violation = violation;
    }

    public ConcurrencyException(@Nullable String message, @Nullable DCBViolation violation) {
        super(message);
        this.command = null;
        this.violation = violation;
    }
}
