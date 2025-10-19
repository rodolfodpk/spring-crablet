package com.crablet.core;

/**
 * Exception thrown when command validation fails.
 * 
 * This distinguishes validation errors (expected, user-facing) from
 * programming errors (bugs). Includes command context for debugging.
 */
public class InvalidCommandException extends RuntimeException {
    private final Command command;
    private final String validationError;
    
    public InvalidCommandException(String message, Command command) {
        super(message);
        this.command = command;
        this.validationError = message;
    }
    
    public InvalidCommandException(String message, String validationError) {
        super(message);
        this.command = null;
        this.validationError = validationError;
    }
    
    public Command getCommand() {
        return command;
    }
    
    public String getValidationError() {
        return validationError;
    }
}
