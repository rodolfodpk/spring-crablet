package com.crablet.automations.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when an automation execution fails.
 */
public record AutomationExecutionErrorMetric(String automationName) implements MetricEvent {
}
