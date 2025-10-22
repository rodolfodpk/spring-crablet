package com.crablet.outbox;

/**
 * Interface for processing outbox entries.
 * Framework-agnostic contract for outbox polling and publishing.
 */
public interface OutboxProcessor {
    
    /**
     * Process pending outbox entries.
     * Fetches pending entries, publishes via configured publishers,
     * and updates outbox status.
     * 
     * @return Number of entries processed
     */
    int processPending();
    
    /**
     * Mark outbox entry as published.
     */
    void markPublished(long outboxId);
    
    /**
     * Mark outbox entry as failed.
     */
    void markFailed(long outboxId, String error);
}
