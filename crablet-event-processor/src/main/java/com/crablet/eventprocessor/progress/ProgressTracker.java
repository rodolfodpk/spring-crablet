package com.crablet.eventprocessor.progress;

/**
 * Tracks processing progress for a processor.
 * 
 * @param <I> Processor identifier type
 */
public interface ProgressTracker<I> {
    
    /**
     * Get last processed position.
     */
    long getLastPosition(I processorId);
    
    /**
     * Update progress after successful processing.
     */
    void updateProgress(I processorId, long position);
    
    /**
     * Record an error.
     * 
     * @param processorId Processor identifier
     * @param error Error message
     * @param maxErrors Maximum errors before marking as FAILED
     */
    void recordError(I processorId, String error, int maxErrors);
    
    /**
     * Reset error count.
     */
    void resetErrorCount(I processorId);
    
    /**
     * Get processor status.
     */
    ProcessorStatus getStatus(I processorId);
    
    /**
     * Set processor status.
     */
    void setStatus(I processorId, ProcessorStatus status);
    
    /**
     * Auto-register processor if not exists.
     * 
     * @param processorId Processor identifier
     * @param instanceId Instance ID for leader tracking
     */
    void autoRegister(I processorId, String instanceId);
}

