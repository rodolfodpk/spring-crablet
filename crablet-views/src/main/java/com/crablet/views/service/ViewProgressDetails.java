package com.crablet.views.service;

import com.crablet.eventpoller.progress.ProcessorStatus;
import org.jspecify.annotations.Nullable;
import java.time.Instant;

/**
 * Detailed progress information for a view projection.
 * Contains all fields from the view_progress table.
 *
 * <p>This record provides comprehensive monitoring information including:
 * <ul>
 *   <li>Processing status and position</li>
 *   <li>Error information (count, message, timestamp)</li>
 *   <li>Instance information (which instance is processing)</li>
 *   <li>Timestamps (last update, creation)</li>
 * </ul>
 */
public record ViewProgressDetails(
    String viewName,
    @Nullable String instanceId,
    ProcessorStatus status,
    long lastPosition,
    int errorCount,
    @Nullable String lastError,
    @Nullable Instant lastErrorAt,
    Instant lastUpdatedAt,
    Instant createdAt
) {}
