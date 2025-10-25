package com.wallets.features.deposit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for depositing money into a wallet.
 */
public record DepositRequest(
        @NotBlank(message = "Deposit ID cannot be empty")
        String depositId,

        @Positive(message = "Deposit amount must be positive")
        int amount,

        @NotBlank(message = "Description cannot be empty")
        String description
) {
}
