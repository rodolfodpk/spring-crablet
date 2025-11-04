package com.crablet.eventstore.metrics;

/**
 * Metric event published when events are appended to the event store.
 * <p>
 * Published by EventStoreImpl when events are successfully appended.
 */
public record EventsAppendedMetric(int count) implements MetricEvent {
    /**
     * Create a metric event for events appended.
     * 
     * @param count The number of events appended (must be > 0)
     */
    public EventsAppendedMetric {
        if (count < 0) {
            throw new IllegalArgumentException("Event count cannot be negative");
        }
    }
}

