package com.crablet.eventpoller.progress;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tracks processing progress for a processor.
 * 
 * @param <I> Processor identifier type
 */
public interface ProgressTracker<I> {
    
    /**
     * Get last processed position.
     */
    long getLastPosition(@NonNull I processorId);

    /**
     * Update progress after successful processing.
     */
    void updateProgress(@NonNull I processorId, long position);

    /**
     * Record an error.
     *
     * @param processorId Processor identifier
     * @param error Error message
     * @param maxErrors Maximum errors before marking as FAILED
     */
    void recordError(@NonNull I processorId, @Nullable String error, int maxErrors);

    /**
     * Reset error count.
     */
    void resetErrorCount(@NonNull I processorId);

    /**
     * Get processor status.
     */
    @NonNull ProcessorStatus getStatus(@NonNull I processorId);

    /**
     * Set processor status.
     */
    void setStatus(@NonNull I processorId, @NonNull ProcessorStatus status);

    /**
     * Auto-register processor if not exists.
     *
     * @param processorId Processor identifier
     * @param instanceId Instance ID for leader tracking
     */
    void autoRegister(@NonNull I processorId, @NonNull String instanceId);
}

