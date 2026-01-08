package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO for wallet information.
 */
@Schema(description = "Wallet information")
public record WalletResponse(
        @Schema(description = "Wallet identifier", example = "wallet-123")
        String walletId,
        
        @Schema(description = "Wallet owner name", example = "John Doe")
        String owner,
        
        @Schema(description = "Current wallet balance", example = "150")
        int balance,
        
        @Schema(description = "Last updated timestamp")
        Instant lastUpdatedAt
) {
}

