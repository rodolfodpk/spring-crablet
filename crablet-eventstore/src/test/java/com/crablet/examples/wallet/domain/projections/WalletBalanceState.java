package com.crablet.examples.wallet.domain.projections;

/**
 * Minimal state for wallet balance - only balance + existence.
 * <p>
 * This record contains only what command handlers need to make decisions
 * about wallet operations, following the DCB principle of projecting
 * only the minimal state required.
 */
public record WalletBalanceState(String walletId, int balance, boolean exists) {

    /**
     * Check if the wallet has sufficient funds for the given amount.
     */
    public boolean hasSufficientFunds(int amount) {
        return exists && balance >= amount;
    }

    /**
     * Check if the wallet exists.
     */
    public boolean isExisting() {
        return exists;
    }
}
