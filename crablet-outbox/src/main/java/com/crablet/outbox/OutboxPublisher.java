package com.crablet.outbox;

import com.crablet.eventstore.StoredEvent;

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
    void publishBatch(List<StoredEvent> events) throws PublishException;
    
    /**
     * Check if publisher is healthy.
     */
    boolean isHealthy();
    
    /**
     * Publishing mode preference.
     */
    default PublishMode getPreferredMode() {
        return PublishMode.BATCH;
    }
    
    enum PublishMode {
        BATCH,
        INDIVIDUAL
    }
}
