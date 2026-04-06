package com.crablet.automations.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

import java.time.Duration;

/**
 * Metric event published after a successful batch execution for an automation.
 */
public record AutomationExecutionMetric(String automationName, int eventsProcessed, Duration duration) implements MetricEvent {
}
