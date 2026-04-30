package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO for detailed view progress information.
 */
@Schema(description = "Detailed view progress information")
public record ViewProgressDetailsResponse(
        @Schema(description = "View name", example = "wallet-balance-view")
        String viewName,
        
        @Schema(description = "Instance ID processing this view", example = "instance-123", nullable = true)
        String instanceId,
        
        @Schema(description = "Current status", example = "ACTIVE", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,
        
        @Schema(description = "Last processed event position", example = "1000")
        long lastPosition,
        
        @Schema(description = "Number of consecutive errors", example = "0")
        int errorCount,
        
        @Schema(description = "Last error message", example = "SQL constraint violation", nullable = true)
        String lastError,
        
        @Schema(description = "When last error occurred", nullable = true)
        Instant lastErrorAt,
        
        @Schema(description = "When progress was last updated")
        Instant lastUpdatedAt,
        
        @Schema(description = "When view was first registered")
        Instant createdAt
) {
    /**
     * Create from ViewProgressDetails service record.
     */
    public static ViewProgressDetailsResponse from(com.crablet.views.service.ViewProgressDetails details) {
        return new ViewProgressDetailsResponse(
                details.viewName(),
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
