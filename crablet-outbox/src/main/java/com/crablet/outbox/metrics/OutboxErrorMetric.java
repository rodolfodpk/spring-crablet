package com.crablet.outbox.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when an outbox publishing error occurs.
 * <p>
 * Published by OutboxPublishingServiceImpl when publishing fails.
 */
public record OutboxErrorMetric(String publisherName) implements MetricEvent {
    /**
     * Create a metric event for outbox error.
     * 
     * @param publisherName The publisher name (must not be null or empty)
     */
    public OutboxErrorMetric {
        if (publisherName == null || publisherName.isEmpty()) {
            throw new IllegalArgumentException("Publisher name cannot be null or empty");
        }
    }
}

