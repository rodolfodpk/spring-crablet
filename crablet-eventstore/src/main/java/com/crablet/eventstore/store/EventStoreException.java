package com.crablet.eventstore.store;

import org.jspecify.annotations.Nullable;

/**
 * Exception thrown when EventStore operations fail due to infrastructure issues
 * (database connectivity, transaction failures, etc.).
 * 
 * This distinguishes infrastructure failures (potentially retryable) from
 * programming errors (bugs that need fixing).
 */
public class EventStoreException extends RuntimeException {

    public EventStoreException(@Nullable String message) {
        super(message);
    }

    public EventStoreException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
