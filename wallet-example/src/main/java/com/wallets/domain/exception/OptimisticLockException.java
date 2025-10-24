package com.wallets.domain.exception;

/**
 * Exception thrown when optimistic concurrency control fails.
 * This occurs when the AppendCondition cursor doesn't match the current state,
 * indicating the state changed between projection and append.
 */
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }
}
