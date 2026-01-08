package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for transferring money between wallets.
 */
@Schema(description = "Request to transfer money between wallets")
public record TransferRequest(
        @NotBlank(message = "Transfer ID is required")
        @Schema(description = "Unique transfer identifier", example = "transfer-101", required = true)
        String transferId,
        
        @NotBlank(message = "From wallet ID is required")
        @Schema(description = "Source wallet identifier", example = "wallet-123", required = true)
        String fromWalletId,
        
        @NotBlank(message = "To wallet ID is required")
        @Schema(description = "Destination wallet identifier", example = "wallet-456", required = true)
        String toWalletId,
        
        @Min(value = 1, message = "Amount must be positive")
        @Schema(description = "Transfer amount", example = "30", required = true)
        int amount,
        
        @NotBlank(message = "Description is required")
        @Schema(description = "Transfer description", example = "Payment for services", required = true)
        String description
) {
}

