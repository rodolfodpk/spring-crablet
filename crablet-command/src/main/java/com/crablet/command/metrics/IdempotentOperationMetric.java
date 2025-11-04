package com.crablet.command.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when an idempotent operation is detected.
 * <p>
 * Published by CommandExecutorImpl when a duplicate operation is handled gracefully
 * (e.g., duplicate wallet creation detected via idempotency check).
 */
public record IdempotentOperationMetric(String commandType) implements MetricEvent {
    /**
     * Create a metric event for idempotent operation.
     * 
     * @param commandType The command type name (must not be null or empty)
     */
    public IdempotentOperationMetric {
        if (commandType == null || commandType.isEmpty()) {
            throw new IllegalArgumentException("Command type cannot be null or empty");
        }
    }
}

