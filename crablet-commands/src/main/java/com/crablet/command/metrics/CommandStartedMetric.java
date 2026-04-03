package com.crablet.command.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

import java.time.Instant;

/**
 * Metric event published when a command execution starts.
 * <p>
 * Published by CommandExecutorImpl at the start of command execution.
 * Uses ClockProvider for consistent, testable timing.
 */
public record CommandStartedMetric(String commandType, Instant startTime) implements MetricEvent {
    /**
     * Create a metric event for command start.
     * 
     * @param commandType The command type name (must not be null or empty)
     * @param startTime The start time from ClockProvider (must not be null)
     */
    public CommandStartedMetric {
        if (commandType == null || commandType.isEmpty()) {
            throw new IllegalArgumentException("Command type cannot be null or empty");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
    }
}

