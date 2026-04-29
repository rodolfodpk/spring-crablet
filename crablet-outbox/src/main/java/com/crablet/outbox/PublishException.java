package com.crablet.outbox;

import com.crablet.eventstore.Stable;

/**
 * Exception thrown when event publishing fails.
 */
@Stable
public class PublishException extends Exception {
    public PublishException(String message) {
        super(message);
    }

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
