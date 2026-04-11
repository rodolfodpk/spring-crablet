package com.crablet.command.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

import java.time.Duration;

/**
 * Metric event published when a command execution succeeds.
 * <p>
 * Published by CommandExecutorImpl after successful command execution.
 * Duration is calculated using ClockProvider for consistent timing.
 * <p>
 * Operation type values: {@code "commutative"}, {@code "commutative_guarded"},
 * {@code "non_commutative"}, {@code "idempotent"}, {@code "no_op"}.
 */
public record CommandSuccessMetric(String commandType, Duration duration, String operationType)
        implements MetricEvent {
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
        if (operationType == null || operationType.isEmpty()) {
            throw new IllegalArgumentException("Operation type cannot be null or empty");
        }
    }
}
