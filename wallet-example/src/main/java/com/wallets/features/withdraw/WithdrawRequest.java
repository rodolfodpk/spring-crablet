package com.wallets.features.withdraw;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for withdrawing money from a wallet.
 */
public record WithdrawRequest(
        @NotBlank(message = "Withdrawal ID cannot be empty")
        String withdrawalId,

        @Positive(message = "Withdrawal amount must be positive")
        int amount,

        @NotBlank(message = "Description cannot be empty")
        String description
) {
}
