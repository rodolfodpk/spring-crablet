package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for view status information.
 */
@Schema(description = "View status information")
public record ViewStatusResponse(
        @Schema(description = "View name", example = "wallet-balance-view")
        String viewName,
        
        @Schema(description = "Current status", example = "ACTIVE", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,
        
        @Schema(description = "Number of events behind", example = "0")
        Long lag
) {
}
