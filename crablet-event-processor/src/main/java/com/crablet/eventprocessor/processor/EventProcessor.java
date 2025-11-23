package com.crablet.eventprocessor.processor;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import java.util.Map;

/**
 * Generic event processor interface.
 * 
 * @param <T> Processor configuration type
 * @param <I> Processor identifier type
 */
public interface EventProcessor<T extends ProcessorConfig<I>, I> {
    
    /**
     * Process events for a specific processor instance.
     * 
     * @param processorId Processor identifier
     * @return Number of events processed
     */
    int process(I processorId);
    
    /**
     * Start all processors.
     */
    void start();
    
    /**
     * Stop all processors.
     */
    void stop();
    
    /**
     * Pause a specific processor.
     */
    void pause(I processorId);
    
    /**
     * Resume a specific processor.
     */
    void resume(I processorId);
    
    /**
     * Get status of a processor.
     */
    ProcessorStatus getStatus(I processorId);
    
    /**
     * Get all processor statuses.
     */
    Map<I, ProcessorStatus> getAllStatuses();
}

