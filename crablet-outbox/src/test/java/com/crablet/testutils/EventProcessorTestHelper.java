package com.crablet.testutils;

import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventprocessor.processor.ProcessorConfig;

import java.util.Map;

/**
 * Test helper for working with generic EventProcessor in tests.
 * Provides convenience methods for processing all processors.
 */
public class EventProcessorTestHelper {
    
    /**
     * Process all processors in the config map.
     * Equivalent to the old processPending() method.
     * 
     * @param eventProcessor The generic event processor
     * @param configs Map of processor IDs to configs
     * @return Total number of events processed across all processors
     */
    public static <T extends ProcessorConfig<I>, I> int processAll(
            EventProcessor<T, I> eventProcessor,
            Map<I, T> configs) {
        int totalProcessed = 0;
        for (I processorId : configs.keySet()) {
            try {
                int processed = eventProcessor.process(processorId);
                totalProcessed += processed;
            } catch (Exception e) {
                // Log but continue processing other processors
                System.err.println("Error processing " + processorId + ": " + e.getMessage());
            }
        }
        return totalProcessed;
    }
}

