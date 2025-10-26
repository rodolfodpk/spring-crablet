package com.crablet.wallet.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * MoneyTransferred represents a money transfer between wallets.
 * This is the event data structure.
 */
public record MoneyTransferred(
        @JsonProperty("transfer_id") String transferId,
        @JsonProperty("from_wallet_id") String fromWalletId,
        @JsonProperty("to_wallet_id") String toWalletId,
        @JsonProperty("amount") int amount,
        @JsonProperty("from_balance") int fromBalance,
        @JsonProperty("to_balance") int toBalance,
        @JsonProperty("transferred_at") Instant transferredAt,
        @JsonProperty("description") String description
) implements WalletEvent {

    public MoneyTransferred {
        if (transferId == null || transferId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transfer ID cannot be null or empty");
        }
        if (fromWalletId == null || fromWalletId.trim().isEmpty()) {
            throw new IllegalArgumentException("From wallet ID cannot be null or empty");
        }
        if (toWalletId == null || toWalletId.trim().isEmpty()) {
            throw new IllegalArgumentException("To wallet ID cannot be null or empty");
        }
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (fromBalance < 0 || toBalance < 0) {
            throw new IllegalArgumentException("Wallet balances cannot be negative");
        }
        if (transferredAt == null) {
            throw new IllegalArgumentException("Transferred at timestamp cannot be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("Description cannot be null");
        }
    }

    /**
     * Create a MoneyTransferred event.
     */
    public static MoneyTransferred of(
            String transferId,
            String fromWalletId,
            String toWalletId,
            int amount,
            int fromBalance,
            int toBalance,
            String description
    ) {
        return new MoneyTransferred(
                transferId,
                fromWalletId,
                toWalletId,
                amount,
                fromBalance,
                toBalance,
                Instant.now(),
                description
        );
    }

    @Override
    public String getEventType() {
        return "MoneyTransferred";
    }

    @Override
    public Instant getOccurredAt() {
        return transferredAt;
    }
}
