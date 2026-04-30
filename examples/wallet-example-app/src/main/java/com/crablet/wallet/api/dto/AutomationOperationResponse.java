package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for automation management operations (pause, resume, reset).
 */
@Schema(description = "Result of an automation management operation")
public record AutomationOperationResponse(
        @Schema(description = "Automation name", example = "wallet-notification")
        String automationName,

        @Schema(description = "Current status after the operation", example = "PAUSED", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,

        @Schema(description = "Human-readable result message", example = "Automation paused successfully")
        String message
) {}
