package com.crablet.core;

/**
 * Value object representing a DCB (Dynamic Consistency Boundary) violation.
 * Captures structured error information from the append_events_if PL/SQL function.
 * 
 * This provides rich context for debugging concurrency conflicts and can inform
 * retry strategies.
 */
public class DCBViolation {
    private final String errorCode;
    private final String message;
    private final int matchingEventsCount;
    
    public DCBViolation(String errorCode, String message, int matchingEventsCount) {
        this.errorCode = errorCode;
        this.message = message;
        this.matchingEventsCount = matchingEventsCount;
    }
    
    /**
     * Error code from PL/SQL function (e.g., "DCB_VIOLATION").
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Human-readable error message.
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Number of conflicting events that caused the violation.
     * Can inform retry strategies (more conflicts = longer backoff).
     */
    public int getMatchingEventsCount() {
        return matchingEventsCount;
    }
    
    /**
     * Check if this is a DCB violation (vs other error types).
     */
    public boolean isDCBViolation() {
        return "DCB_VIOLATION".equals(errorCode);
    }
    
    @Override
    public String toString() {
        return String.format("DCBViolation{errorCode='%s', message='%s', matchingEvents=%d}",
                errorCode, message, matchingEventsCount);
    }
}
