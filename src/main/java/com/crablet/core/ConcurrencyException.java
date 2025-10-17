package com.crablet.core;

/**
 * Exception thrown when AppendCondition fails due to concurrent modification.
 * This is equivalent to go-crablet's concurrency violation handling.
 */
public class ConcurrencyException extends RuntimeException {
    private final Command command;
    
    public ConcurrencyException(String message) {
        super(message);
        this.command = null;
    }
    
    public ConcurrencyException(String message, Throwable cause) {
        super(message, cause);
        this.command = null;
    }
    
    public ConcurrencyException(String message, Command command) {
        super(message);
        this.command = command;
    }
    
    public ConcurrencyException(String message, Command command, Throwable cause) {
        super(message, cause);
        this.command = command;
    }
    
    public Command getCommand() {
        return command;
    }
}
