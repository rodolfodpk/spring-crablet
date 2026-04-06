package com.crablet.eventpoller.metrics;

/**
 * Metric event published when a processor's backoff state changes.
 * Published when backoff activates (empty poll threshold exceeded) or resets (events found).
 */
public record BackoffStateMetric(String processorId, String instanceId, boolean active, int emptyPollCount) implements ProcessorMetric {
}
