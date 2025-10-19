package com.wallets.features.query;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response object for wallet commands with pagination.
 */
@Schema(description = "Wallet commands response with pagination")
public record WalletCommandsResponse(
        @Schema(description = "List of commands with their events")
        List<WalletCommandDTO> commands,

        @Schema(description = "Current page number (0-based)")
        int page,

        @Schema(description = "Page size")
        int size,

        @Schema(description = "Total number of commands")
        long totalCommands,

        @Schema(description = "Whether there are more commands available")
        boolean hasNext
) {
}
