package com.crablet.eventpoller.processor;

/**
 * Configuration for a processor instance.
 * 
 * @param <I> Processor identifier type (e.g., String, TopicPublisherPair)
 */
public interface ProcessorConfig<I> {
    /**
     * Unique identifier for this processor instance.
     */
    I getProcessorId();
    
    /**
     * Polling interval in milliseconds.
     */
    long getPollingIntervalMs();
    
    /**
     * Batch size for processing events.
     */
    int getBatchSize();
    
    /**
     * Whether exponential backoff is enabled.
     */
    boolean isBackoffEnabled();
    
    /**
     * Backoff threshold (empty polls before backoff starts).
     */
    int getBackoffThreshold();
    
    /**
     * Backoff multiplier.
     */
    int getBackoffMultiplier();
    
    /**
     * Maximum backoff in seconds.
     */
    int getBackoffMaxSeconds();

    /**
     * Maximum consecutive errors before the processor is marked as failed.
     */
    default int getMaxErrors() {
        return 10;
    }

    /**
     * How often a follower retries leader acquisition while idle.
     */
    default long getLeaderElectionRetryIntervalMs() {
        return 30000L;
    }
    
    /**
     * Whether processor is enabled.
     */
    boolean isEnabled();
}
