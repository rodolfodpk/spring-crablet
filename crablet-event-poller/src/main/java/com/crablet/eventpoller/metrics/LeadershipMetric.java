package com.crablet.eventpoller.metrics;

/**
 * Metric event published when processor leadership status changes.
 */
public record LeadershipMetric(String processorId, String instanceId, boolean isLeader) implements ProcessorMetric {
    /**
     * Create a metric event for leadership status.
     *
     * @param processorId The processor identifier (e.g. "outbox", "views", automation name)
     * @param instanceId The instance identifier (must not be null or empty)
     * @param isLeader Whether this instance is the leader
     */
    public LeadershipMetric {
        if (processorId == null || processorId.isEmpty()) {
            throw new IllegalArgumentException("Processor ID cannot be null or empty");
        }
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("Instance ID cannot be null or empty");
        }
    }
}

