package com.crablet.eventprocessor;

import com.crablet.eventstore.store.StoredEvent;

import java.util.List;

/**
 * Fetches events for a processor.
 * Uses read replica DataSource for optimal read performance.
 * 
 * @param <I> Processor identifier type
 */
public interface EventFetcher<I> {
    
    /**
     * Fetch events for a processor after a given position.
     * 
     * @param processorId Processor identifier
     * @param lastPosition Last processed position
     * @param batchSize Maximum number of events to fetch
     * @return List of events to process
     */
    List<StoredEvent> fetchEvents(I processorId, long lastPosition, int batchSize);
}

