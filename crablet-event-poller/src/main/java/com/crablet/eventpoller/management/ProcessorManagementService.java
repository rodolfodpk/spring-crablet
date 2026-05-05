package com.crablet.eventpoller.management;

import com.crablet.eventpoller.progress.ProcessorStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Service for managing processor operations.
 * Provides high-level operations for pausing, resuming, and monitoring processors.
 * 
 * @param <I> Processor identifier type
 */
public interface ProcessorManagementService<I> {
    
    /**
     * Pause a processor (stops processing events).
     * 
     * @param processorId Processor identifier
     * @return true if processor was paused, false if not found
     */
    boolean pause(@NonNull I processorId);

    /**
     * Resume a processor (starts processing events again).
     *
     * @param processorId Processor identifier
     * @return true if processor was resumed, false if not found
     */
    boolean resume(@NonNull I processorId);

    /**
     * Reset a failed processor (clears error count and resumes).
     *
     * @param processorId Processor identifier
     * @return true if processor was reset, false if not found
     */
    boolean reset(@NonNull I processorId);

    /**
     * Get status of a specific processor.
     *
     * @param processorId Processor identifier
     * @return Processor status ({@linkplain ProcessorStatus#ACTIVE} when no row exists yet)
     */
    @NonNull ProcessorStatus getStatus(@NonNull I processorId);

    /**
     * Get status of all processors.
     *
     * @return Map of processor ID to status
     */
    @NonNull Map<I, ProcessorStatus> getAllStatuses();

    /**
     * Get lag for a processor (how far behind the latest event).
     *
     * @param processorId Processor identifier
     * @return Lag (current max position - last processed position), or null if not found
     */
    @Nullable Long getLag(@NonNull I processorId);

    /**
     * Get backoff information for a processor.
     *
     * @param processorId Processor identifier
     * @return Backoff information or null if not found or backoff not enabled
     */
    @Nullable BackoffInfo getBackoffInfo(@NonNull I processorId);

    /**
     * Get backoff information for all processors.
     *
     * @return Map of processor ID to backoff information
     */
    @NonNull Map<I, BackoffInfo> getAllBackoffInfo();
    
    /**
     * Backoff information for a processor.
     */
    record BackoffInfo(
        int emptyPollCount,
        int currentSkipCounter
    ) {
        public boolean isBackedOff() {
            return currentSkipCounter > 0;
        }
    }
}

