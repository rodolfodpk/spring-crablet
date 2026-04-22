package com.crablet.automations.management;

import com.crablet.eventpoller.progress.ProcessorStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Detailed progress information for an automation, read from the {@code automation_progress} table.
 */
public record AutomationProgressDetails(
        String automationName,
        @Nullable String instanceId,
        ProcessorStatus status,
        long lastPosition,
        int errorCount,
        @Nullable String lastError,
        @Nullable Instant lastErrorAt,
        Instant lastUpdatedAt,
        Instant createdAt
) {}
