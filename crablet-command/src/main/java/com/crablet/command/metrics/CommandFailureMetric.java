package com.crablet.command.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when a command execution fails.
 * <p>
 * Published by CommandExecutorImpl when command execution fails with an exception.
 */
public record CommandFailureMetric(String commandType, String errorType) implements MetricEvent {
    /**
     * Create a metric event for failed command execution.
     * 
     * @param commandType The command type name (must not be null or empty)
     * @param errorType The error type (e.g., "validation", "concurrency", "runtime") (must not be null or empty)
     */
    public CommandFailureMetric {
        if (commandType == null || commandType.isEmpty()) {
            throw new IllegalArgumentException("Command type cannot be null or empty");
        }
        if (errorType == null || errorType.isEmpty()) {
            throw new IllegalArgumentException("Error type cannot be null or empty");
        }
    }
}

