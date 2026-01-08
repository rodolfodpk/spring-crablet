package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for opening a new wallet.
 */
@Schema(description = "Request to open a new wallet")
public record OpenWalletRequest(
        @NotBlank(message = "Wallet ID is required")
        @Schema(description = "Unique wallet identifier", example = "wallet-123", required = true)
        String walletId,
        
        @NotBlank(message = "Owner is required")
        @Schema(description = "Wallet owner name", example = "John Doe", required = true)
        String owner,
        
        @Min(value = 0, message = "Initial balance must be non-negative")
        @Schema(description = "Initial wallet balance", example = "100", required = true)
        int initialBalance
) {
}

