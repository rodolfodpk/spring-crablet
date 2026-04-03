package com.crablet.wallet.api.dto;

import com.crablet.outbox.management.OutboxProgressDetails;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO for detailed outbox progress information.
 */
@Schema(description = "Detailed outbox publisher progress information")
public record OutboxProgressDetailsResponse(
        @Schema(description = "Topic name", example = "wallet-events")
        String topic,

        @Schema(description = "Publisher name", example = "LogPublisher")
        String publisher,

        @Schema(description = "Current status", example = "ACTIVE", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,

        @Schema(description = "Last published event position", example = "1000")
        long lastPosition,

        @Schema(description = "When last event was published", nullable = true)
        Instant lastPublishedAt,

        @Schema(description = "Number of consecutive errors", example = "0")
        int errorCount,

        @Schema(description = "Last error message", nullable = true)
        String lastError,

        @Schema(description = "When progress was last updated")
        Instant updatedAt,

        @Schema(description = "Instance ID of the current leader", nullable = true)
        String leaderInstance
) {
    public static OutboxProgressDetailsResponse from(OutboxProgressDetails details) {
        return new OutboxProgressDetailsResponse(
                details.topic(),
                details.publisher(),
                details.status().name(),
                details.lastPosition(),
                details.lastPublishedAt(),
                details.errorCount(),
                details.lastError(),
                details.updatedAt(),
                details.leaderInstance()
        );
    }
}
