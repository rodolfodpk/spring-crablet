package com.crablet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for depositing money into a wallet.
 */
@Schema(description = "Request to deposit money into a wallet")
public record DepositRequest(
        @NotBlank(message = "Deposit ID is required")
        @Schema(description = "Unique deposit identifier", example = "deposit-456", required = true)
        String depositId,
        
        @Min(value = 1, message = "Amount must be positive")
        @Schema(description = "Deposit amount", example = "50", required = true)
        int amount,
        
        @NotBlank(message = "Description is required")
        @Schema(description = "Deposit description", example = "Salary payment", required = true)
        String description
) {
}

