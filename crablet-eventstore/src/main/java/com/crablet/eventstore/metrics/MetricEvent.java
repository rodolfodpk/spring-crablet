package com.crablet.eventstore.metrics;

/**
 * Base interface for all metric events.
 * Metric events are framework-agnostic data records published via Spring Events.
 * <p>
 * All metric events should implement this interface to allow type-safe handling.
 */
public interface MetricEvent {
    // Marker interface - no methods needed
}

