package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for automation status.
 */
@Schema(description = "Automation status information")
public record AutomationStatusResponse(
        @Schema(description = "Automation name", example = "wallet-notification")
        String automationName,

        @Schema(description = "Current status", example = "ACTIVE", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,

        @Schema(description = "Number of events behind the latest position", example = "0")
        Long lag
) {}
