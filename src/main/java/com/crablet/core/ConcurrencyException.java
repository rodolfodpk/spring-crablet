package com.crablet.core;

/**
 * Exception thrown when AppendCondition fails due to concurrent modification.
 * This is equivalent to go-crablet's concurrency violation handling.
 * 
 * See also:
 * - EventStoreException - for infrastructure failures
 * - InvalidCommandException - for validation errors
 */
public class ConcurrencyException extends RuntimeException {
    private final Command command;
    private final DCBViolation violation;

    public ConcurrencyException(String message) {
        super(message);
        this.command = null;
        this.violation = null;
    }

    public ConcurrencyException(String message, Throwable cause) {
        super(message, cause);
        this.command = null;
        this.violation = null;
    }

    public ConcurrencyException(String message, Command command) {
        super(message);
        this.command = command;
        this.violation = null;
    }

    public ConcurrencyException(String message, Command command, Throwable cause) {
        super(message, cause);
        this.command = command;
        this.violation = null;
    }

    public ConcurrencyException(String message, Command command, DCBViolation violation) {
        super(message);
        this.command = command;
        this.violation = violation;
    }

    public ConcurrencyException(String message, DCBViolation violation) {
        super(message);
        this.command = null;
        this.violation = violation;
    }

    public Command getCommand() {
        return command;
    }

    public DCBViolation getViolation() {
        return violation;
    }
}
