package com.crablet.core;

/**
 * Value object representing a DCB (Dynamic Consistency Boundary) violation.
 * Captures structured error information from the append_events_if PL/SQL function.
 * <p>
 * This provides rich context for debugging concurrency conflicts and can inform
 * retry strategies.
 */
public record DCBViolation(String errorCode, String message, int matchingEventsCount) {
    
    @Override
    public String toString() {
        return String.format("DCBViolation{errorCode='%s', message='%s', matchingEvents=%d}",
                errorCode, message, matchingEventsCount);
    }
}
