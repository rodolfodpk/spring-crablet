package com.wallets.features.query;

import java.time.Instant;

/**
 * WalletState holds the state for a wallet.
 * This is reconstructed from events using state projectors.
 */
public record WalletState(
        String walletId,
        String owner,
        int balance,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create an empty wallet state.
     */
    public static WalletState empty() {
        return new WalletState("", "", 0, null, null);
    }

    /**
     * Check if this wallet state is empty (not initialized).
     */
    public boolean isEmpty() {
        return walletId.isEmpty() || owner.isEmpty();
    }

    /**
     * Update the balance and timestamp.
     */
    public WalletState withBalance(int newBalance, Instant updatedAt) {
        return new WalletState(walletId, owner, newBalance, createdAt, updatedAt);
    }
}