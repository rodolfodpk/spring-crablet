package com.wallets.domain.exception;

/**
 * Exception thrown when wallet operations fail validation (negative amounts, invalid parameters, etc.).
 */
public class InvalidOperationException extends RuntimeException {

    private final String operation;
    private final String reason;

    public InvalidOperationException(String operation, String reason) {
        super(String.format("Invalid %s operation: %s", operation, reason));
        this.operation = operation;
        this.reason = reason;
    }

    public InvalidOperationException(String operation, String reason, String message) {
        super(message);
        this.operation = operation;
        this.reason = reason;
    }

    public InvalidOperationException(String message) {
        super(message);
        this.operation = "unknown";
        this.reason = message;
    }

    public String getOperation() {
        return operation;
    }

    public String getReason() {
        return reason;
    }
}
