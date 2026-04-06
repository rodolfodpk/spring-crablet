package com.crablet.views.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when a view projection fails.
 */
public record ViewProjectionErrorMetric(String viewName) implements MetricEvent {
}
