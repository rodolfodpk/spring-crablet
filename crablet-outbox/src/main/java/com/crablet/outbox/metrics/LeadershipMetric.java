package com.crablet.outbox.metrics;

import com.crablet.eventstore.metrics.MetricEvent;

/**
 * Metric event published when outbox leadership status changes.
 * <p>
 * Published by OutboxProcessorImpl and OutboxLeaderElector when leadership changes.
 */
public record LeadershipMetric(String instanceId, boolean isLeader) implements MetricEvent {
    /**
     * Create a metric event for leadership status.
     * 
     * @param instanceId The instance identifier (must not be null or empty)
     * @param isLeader Whether this instance is the leader
     */
    public LeadershipMetric {
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("Instance ID cannot be null or empty");
        }
    }
}

