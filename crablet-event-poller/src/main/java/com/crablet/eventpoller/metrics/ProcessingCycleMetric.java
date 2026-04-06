package com.crablet.eventpoller.metrics;

/**
 * Metric event published when a processing cycle completes.
 */
public record ProcessingCycleMetric(String processorId, String instanceId, int eventsProcessed, boolean empty) implements ProcessorMetric {
}

