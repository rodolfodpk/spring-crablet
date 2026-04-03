package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for outbox management operations (pause, resume, reset).
 */
@Schema(description = "Result of an outbox management operation")
public record OutboxOperationResponse(
        @Schema(description = "Topic name", example = "wallet-events")
        String topic,

        @Schema(description = "Publisher name", example = "LogPublisher")
        String publisher,

        @Schema(description = "Current status after the operation", example = "PAUSED", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,

        @Schema(description = "Human-readable result message", example = "Outbox publisher paused successfully")
        String message
) {}
