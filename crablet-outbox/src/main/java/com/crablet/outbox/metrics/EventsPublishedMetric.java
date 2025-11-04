package com.crablet.outbox.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when events are published by an outbox publisher.
 * <p>
 * Published by OutboxPublishingServiceImpl after successful event publishing.
 */
public record EventsPublishedMetric(String publisherName, int count) implements MetricEvent {
    /**
     * Create a metric event for events published.
     * 
     * @param publisherName The publisher name (must not be null or empty)
     * @param count The number of events published (must be > 0)
     */
    public EventsPublishedMetric {
        if (publisherName == null || publisherName.isEmpty()) {
            throw new IllegalArgumentException("Publisher name cannot be null or empty");
        }
        if (count < 0) {
            throw new IllegalArgumentException("Event count cannot be negative");
        }
    }
}

