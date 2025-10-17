package com.wallets.features.query;

import java.time.Instant;

/**
 * Response DTO for wallet state information.
 */
public record WalletResponse(
    String walletId,
    String owner,
    int balance,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * Factory method to create WalletResponse from WalletState.
     */
    public static WalletResponse from(WalletState state) {
        return new WalletResponse(
            state.walletId(),
            state.owner(),
            state.balance(),
            state.createdAt(),
            state.updatedAt()
        );
    }
}
