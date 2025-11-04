package com.crablet.command.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

import java.time.Duration;

/**
 * Metric event published when a command execution succeeds.
 * <p>
 * Published by CommandExecutorImpl after successful command execution.
 * Duration is calculated using ClockProvider for consistent timing.
 */
public record CommandSuccessMetric(String commandType, Duration duration) implements MetricEvent {
    /**
     * Create a metric event for successful command execution.
     * 
     * @param commandType The command type name (must not be null or empty)
     * @param duration The execution duration calculated from ClockProvider (must not be null or negative)
     */
    public CommandSuccessMetric {
        if (commandType == null || commandType.isEmpty()) {
            throw new IllegalArgumentException("Command type cannot be null or empty");
        }
        if (duration == null) {
            throw new IllegalArgumentException("Duration cannot be null");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
    }
}

