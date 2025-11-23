package com.crablet.eventprocessor.metrics;

/**
 * Metric event published when a processing cycle completes.
 */
public record ProcessingCycleMetric() implements ProcessorMetric {
    // Simple marker record - no data needed
}

