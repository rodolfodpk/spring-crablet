package com.wallets.domain.exception;

/**
 * Exception thrown when an operation is attempted with an ID that has already been processed.
 * This is typically used for idempotency violations detected via AppendCondition.
 */
public class DuplicateOperationException extends RuntimeException {
    public DuplicateOperationException(String message) {
        super(message);
    }
}
