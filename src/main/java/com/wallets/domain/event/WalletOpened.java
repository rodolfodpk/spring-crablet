package com.wallets.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * WalletOpened represents when a wallet is opened.
 * This is the event data structure.
 */
public record WalletOpened(
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("owner") String owner,
        @JsonProperty("initial_balance") int initialBalance,
        @JsonProperty("opened_at") Instant openedAt
) implements WalletEvent {

    public WalletOpened {
        if (walletId == null || walletId.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID cannot be null or empty");
        }
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner cannot be null or empty");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("Opened at timestamp cannot be null");
        }
    }

    /**
     * Create a WalletOpened event.
     */
    public static WalletOpened of(String walletId, String owner, int initialBalance) {
        return new WalletOpened(walletId, owner, initialBalance, Instant.now());
    }

    @Override
    public String getEventType() {
        return "WalletOpened";
    }

    @Override
    public Instant getOccurredAt() {
        return openedAt;
    }
}
