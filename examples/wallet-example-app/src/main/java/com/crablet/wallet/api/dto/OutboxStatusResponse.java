package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for outbox publisher status.
 */
@Schema(description = "Outbox publisher status information")
public record OutboxStatusResponse(
        @Schema(description = "Topic name", example = "wallet-events")
        String topic,

        @Schema(description = "Publisher name", example = "LogPublisher")
        String publisher,

        @Schema(description = "Current status", example = "ACTIVE", allowableValues = {"ACTIVE", "PAUSED", "FAILED"})
        String status,

        @Schema(description = "Number of events behind the latest position", example = "0")
        Long lag
) {}
