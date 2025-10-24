package com.wallets.features.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response object for a single event.
 */
@Schema(description = "Event information")
public record EventResponse(
        @Schema(description = "Event type", example = "MoneyTransferred")
        String type,

        @Schema(description = "Event data as JSON")
        Object data,

        @Schema(description = "Event position in the stream")
        Long position,

        @Schema(description = "When the event occurred")
        @JsonProperty("occurred_at")
        Instant occurredAt
) {
}
