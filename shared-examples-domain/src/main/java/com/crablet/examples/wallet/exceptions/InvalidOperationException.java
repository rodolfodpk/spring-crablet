package com.crablet.examples.wallet.exceptions;

/**
 * Exception thrown when wallet operations fail validation (negative amounts, invalid parameters, etc.).
 */
public class InvalidOperationException extends RuntimeException {

    public final String operation;
    public final String reason;

    public InvalidOperationException(String operation, String reason) {
        super(String.format("Invalid %s operation: %s", operation, reason));
        this.operation = operation;
        this.reason = reason;
    }
}

