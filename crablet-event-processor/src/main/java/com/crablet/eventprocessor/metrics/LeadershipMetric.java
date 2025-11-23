package com.crablet.eventprocessor.metrics;

/**
 * Metric event published when processor leadership status changes.
 */
public record LeadershipMetric(String instanceId, boolean isLeader) implements ProcessorMetric {
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

