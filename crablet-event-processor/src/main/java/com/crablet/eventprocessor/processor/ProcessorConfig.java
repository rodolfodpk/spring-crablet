package com.crablet.eventprocessor.processor;

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
     * Whether processor is enabled.
     */
    boolean isEnabled();
}

