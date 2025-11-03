package com.crablet.eventstore.command;

/**
 * Exception thrown when command validation fails.
 * 
 * This distinguishes validation errors (expected, user-facing) from
 * programming errors (bugs). Includes command context for debugging.
 */
public class InvalidCommandException extends RuntimeException {
    public final Object command;
    public final String validationError;
    
    public InvalidCommandException(String message, Object command) {
        super(message);
        this.command = command;
        this.validationError = message;
    }
    
    public InvalidCommandException(String message, String validationError) {
        super(message);
        this.command = null;
        this.validationError = validationError;
    }
    
    public InvalidCommandException(String message, Object command, Throwable cause) {
        super(message, cause);
        this.command = command;
        this.validationError = message;
    }
}
