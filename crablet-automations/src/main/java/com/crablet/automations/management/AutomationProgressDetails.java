package com.crablet.automations.management;

import com.crablet.eventpoller.progress.ProcessorStatus;

import java.time.Instant;

/**
 * Detailed progress information for an automation, read from the {@code reaction_progress} table.
 */
public record AutomationProgressDetails(
        String automationName,
        String instanceId,
        ProcessorStatus status,
        long lastPosition,
        int errorCount,
        String lastError,
        Instant lastErrorAt,
        Instant lastUpdatedAt,
        Instant createdAt
) {}
