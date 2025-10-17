package com.wallets.features.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for transferring money between wallets.
 */
public record TransferRequest(
    @NotBlank(message = "Transfer ID cannot be empty")
    String transferId,
    
    @NotBlank(message = "From wallet ID cannot be empty")
    String fromWalletId,
    
    @NotBlank(message = "To wallet ID cannot be empty")
    String toWalletId,
    
    @Positive(message = "Transfer amount must be positive")
    int amount,
    
    @NotBlank(message = "Description cannot be empty")
    String description
) {}
