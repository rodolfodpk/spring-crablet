package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for withdrawing money from a wallet.
 */
@Schema(description = "Request to withdraw money from a wallet")
public record WithdrawRequest(
        @NotBlank(message = "Withdrawal ID is required")
        @Schema(description = "Unique withdrawal identifier", example = "withdrawal-789", required = true)
        String withdrawalId,
        
        @Min(value = 1, message = "Amount must be positive")
        @Schema(description = "Withdrawal amount", example = "25", required = true)
        int amount,
        
        @NotBlank(message = "Description is required")
        @Schema(description = "Withdrawal description", example = "Purchase at store", required = true)
        String description
) {
}

