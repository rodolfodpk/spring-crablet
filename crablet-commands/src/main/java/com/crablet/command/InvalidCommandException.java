package com.crablet.command;

/**
 * Exception thrown when command validation fails.
 * 
 * This distinguishes validation errors (expected, user-facing) from
 * programming errors (bugs). Includes command context for debugging.
 */
public class InvalidCommandException extends RuntimeException {

    /** The command object that failed validation, for diagnostics. May be {@code null} when only an error string is available. */
    public final @org.jspecify.annotations.Nullable Object command;

    /** Human-readable description of the validation failure. */
    public final String validationError;

    /** Thrown when the command object is available (e.g., from a command handler). */
    public InvalidCommandException(String message, Object command) {
        super(message);
        this.command = command;
        this.validationError = message;
    }

    /** Thrown when only a validation error string is available, without the original command object. */
    public InvalidCommandException(String message, String validationError) {
        super(message);
        this.command = null;
        this.validationError = validationError;
    }

    /** Thrown when wrapping an underlying cause (e.g., a YAVI {@code ConstraintViolationsException}). */
    public InvalidCommandException(String message, Object command, Throwable cause) {
        super(message, cause);
        this.command = command;
        this.validationError = message;
    }
}

