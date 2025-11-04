package com.crablet.outbox.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when an outbox processing cycle completes.
 * <p>
 * Published by OutboxProcessorImpl for each processing cycle.
 */
public record ProcessingCycleMetric() implements MetricEvent {
    // Simple marker record - no data needed
}

