package com.crablet.outbox.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

import java.time.Duration;

/**
 * Metric event published when events are published by an outbox publisher.
 * Includes the duration of the publishing operation.
 * <p>
 * Published by OutboxPublishingServiceImpl after successful event publishing.
 */
public record PublishingDurationMetric(String publisherName, Duration duration) implements MetricEvent {
    /**
     * Create a metric event for publishing duration.
     * 
     * @param publisherName The publisher name (must not be null or empty)
     * @param duration The publishing duration (must not be null or negative)
     */
    public PublishingDurationMetric {
        if (publisherName == null || publisherName.isEmpty()) {
            throw new IllegalArgumentException("Publisher name cannot be null or empty");
        }
        if (duration == null) {
            throw new IllegalArgumentException("Duration cannot be null");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
    }
}

