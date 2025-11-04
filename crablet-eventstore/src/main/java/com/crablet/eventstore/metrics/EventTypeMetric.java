package com.crablet.eventstore.metrics;

/**
 * Metric event published for each event type appended.
 * <p>
 * Published by EventStoreImpl for tracking event type distribution.
 */
public record EventTypeMetric(String eventType) implements MetricEvent {
    /**
     * Create a metric event for an event type.
     * 
     * @param eventType The event type name (must not be null or empty)
     */
    public EventTypeMetric {
        if (eventType == null || eventType.isEmpty()) {
            throw new IllegalArgumentException("Event type cannot be null or empty");
        }
    }
}

