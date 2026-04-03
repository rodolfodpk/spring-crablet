package com.crablet.wallet.api.dto;

import com.crablet.automations.management.AutomationProgressDetails;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO for detailed automation progress information.
 */
@Schema(description = "Detailed automation progress information")
public record AutomationProgressDetailsResponse(
        @Schema(description = "Automation name", example = "wallet-notification")
        String automationName,

        @Schema(description = "Instance ID currently leading this automation", example = "instance-123", nullable = true)
        String instanceId,

        @Schema(description = "Current status", example = "ACTIVE", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,

        @Schema(description = "Last processed event position", example = "1000")
        long lastPosition,

        @Schema(description = "Number of consecutive errors", example = "0")
        int errorCount,

        @Schema(description = "Last error message", nullable = true)
        String lastError,

        @Schema(description = "When last error occurred", nullable = true)
        Instant lastErrorAt,

        @Schema(description = "When progress was last updated")
        Instant lastUpdatedAt,

        @Schema(description = "When automation was first registered")
        Instant createdAt
) {
    public static AutomationProgressDetailsResponse from(AutomationProgressDetails details) {
        return new AutomationProgressDetailsResponse(
                details.automationName(),
                details.instanceId(),
                details.status().name(),
                details.lastPosition(),
                details.errorCount(),
                details.lastError(),
                details.lastErrorAt(),
                details.lastUpdatedAt(),
                details.createdAt()
        );
    }
}
