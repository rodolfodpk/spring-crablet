package com.crablet.eventprocessor.management;

import com.crablet.eventprocessor.progress.ProcessorStatus;

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
    boolean pause(I processorId);
    
    /**
     * Resume a processor (starts processing events again).
     * 
     * @param processorId Processor identifier
     * @return true if processor was resumed, false if not found
     */
    boolean resume(I processorId);
    
    /**
     * Reset a failed processor (clears error count and resumes).
     * 
     * @param processorId Processor identifier
     * @return true if processor was reset, false if not found
     */
    boolean reset(I processorId);
    
    /**
     * Get status of a specific processor.
     * 
     * @param processorId Processor identifier
     * @return Processor status or null if not found
     */
    ProcessorStatus getStatus(I processorId);
    
    /**
     * Get status of all processors.
     * 
     * @return Map of processor ID to status
     */
    Map<I, ProcessorStatus> getAllStatuses();
    
    /**
     * Get lag for a processor (how far behind the latest event).
     * 
     * @param processorId Processor identifier
     * @return Lag (current max position - last processed position), or null if not found
     */
    Long getLag(I processorId);
    
    /**
     * Get backoff information for a processor.
     * 
     * @param processorId Processor identifier
     * @return Backoff information or null if not found or backoff not enabled
     */
    BackoffInfo getBackoffInfo(I processorId);
    
    /**
     * Get backoff information for all processors.
     * 
     * @return Map of processor ID to backoff information
     */
    Map<I, BackoffInfo> getAllBackoffInfo();
    
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

