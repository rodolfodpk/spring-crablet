package com.crablet.outbox.management;

import com.crablet.eventpoller.progress.ProcessorStatus;

import java.time.Instant;

/**
 * Detailed progress information for an outbox topic-publisher pair.
 * Contains all fields from the outbox_topic_progress table.
 */
public record OutboxProgressDetails(
    String topic,
    String publisher,
    ProcessorStatus status,
    long lastPosition,
    Instant lastPublishedAt,
    int errorCount,
    String lastError,
    Instant updatedAt,
    String leaderInstance
) {}
