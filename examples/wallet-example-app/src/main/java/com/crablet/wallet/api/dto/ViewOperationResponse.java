package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for view operation results (pause, resume, reset).
 */
@Schema(description = "View operation result")
public record ViewOperationResponse(
        @Schema(description = "View name", example = "wallet-balance-view")
        String viewName,
        
        @Schema(description = "Current status after operation", example = "PAUSED", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,
        
        @Schema(description = "Operation result message", example = "View projection paused successfully")
        String message
) {
}
