package com.crablet.command.web;

import org.jspecify.annotations.Nullable;

/**
 * Generic HTTP response body for command execution outcomes.
 */
public record CommandApiResponse(
        String status,
        @Nullable String reason
) {
    public static CommandApiResponse created() {
        return new CommandApiResponse("CREATED", null);
    }

    public static CommandApiResponse idempotent(@Nullable String reason) {
        return new CommandApiResponse("IDEMPOTENT", reason);
    }
}
