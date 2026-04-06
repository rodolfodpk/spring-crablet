package com.crablet.views.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

import java.time.Duration;

/**
 * Metric event published after a successful batch projection for a view.
 */
public record ViewProjectionMetric(String viewName, int eventsProjected, Duration duration) implements MetricEvent {
}
