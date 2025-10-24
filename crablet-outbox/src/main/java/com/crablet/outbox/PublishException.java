package com.crablet.outbox;

/**
 * Exception thrown when event publishing fails.
 */
public class PublishException extends Exception {
    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
