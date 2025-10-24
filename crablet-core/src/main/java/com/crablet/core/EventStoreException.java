package com.crablet.core;

/**
 * Exception thrown when EventStore operations fail due to infrastructure issues
 * (database connectivity, transaction failures, etc.).
 * 
 * This distinguishes infrastructure failures (potentially retryable) from
 * programming errors (bugs that need fixing).
 */
public class EventStoreException extends RuntimeException {
    
    public EventStoreException(String message) {
        super(message);
    }
    
    public EventStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
