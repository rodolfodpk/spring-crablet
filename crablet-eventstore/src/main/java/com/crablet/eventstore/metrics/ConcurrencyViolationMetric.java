package com.crablet.eventstore.metrics;

/**
 * Metric event published when a DCB concurrency violation occurs.
 * <p>
 * Published by EventStoreImpl when appendIf() fails due to optimistic locking conflict.
 */
public record ConcurrencyViolationMetric() implements MetricEvent {
    // Simple marker record - no data needed
}

