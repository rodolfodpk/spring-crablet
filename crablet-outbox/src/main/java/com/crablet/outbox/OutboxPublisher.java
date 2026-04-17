package com.crablet.outbox;

import java.util.List;

/**
 * A publisher publishes events from topics to external systems.
 */
public interface OutboxPublisher {
    
    /**
     * Get publisher name for logging/monitoring.
     */
    String getName();
    
    /**
     * Publish a batch of events.
     */
    void publishBatch(List<com.crablet.eventstore.StoredEvent> events) throws PublishException;
    
    /**
     * Check if publisher is healthy.
     */
    boolean isHealthy();
    
    /**
     * Declares whether this publisher prefers receiving a full batch or individual events.
     * Defaults to {@link PublishMode#BATCH}. Override to return {@link PublishMode#INDIVIDUAL}
     * if the external system API is event-per-call (e.g., an HTTP webhook).
     */
    default PublishMode getPreferredMode() {
        return PublishMode.BATCH;
    }
    
    enum PublishMode {
        /**
         * Deliver all events in the polling batch in a single
         * {@link OutboxPublisher#publishBatch} call.
         */
        BATCH,
        /**
         * Deliver each event as a separate {@link OutboxPublisher#publishBatch} call with a
         * single-element list.
         */
        INDIVIDUAL
    }
}
