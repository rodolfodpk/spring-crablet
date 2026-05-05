package com.crablet.eventpoller.processor;

import com.crablet.eventpoller.progress.ProcessorStatus;
import org.jspecify.annotations.NonNull;

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
    int process(@NonNull I processorId);

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
    void pause(@NonNull I processorId);

    /**
     * Resume a specific processor.
     */
    void resume(@NonNull I processorId);

    /**
     * Get status of a processor.
     */
    @NonNull ProcessorStatus getStatus(@NonNull I processorId);

    /**
     * Get all processor statuses.
     */
    @NonNull Map<I, ProcessorStatus> getAllStatuses();
}

