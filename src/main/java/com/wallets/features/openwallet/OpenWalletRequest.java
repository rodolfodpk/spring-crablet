package com.wallets.features.openwallet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for opening a new wallet.
 */
public record OpenWalletRequest(
    @NotBlank(message = "Owner cannot be empty")
    String owner,
    
    @Min(value = 0, message = "Initial balance cannot be negative")
    int initialBalance
) {}
