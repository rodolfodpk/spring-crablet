package com.wallets.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DepositMade represents when money is deposited into a wallet.
 * This is the event data structure.
 */
public record DepositMade(
        @JsonProperty("deposit_id") String depositId,
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("amount") int amount,
        @JsonProperty("new_balance") int newBalance,
        @JsonProperty("deposited_at") Instant depositedAt,
        @JsonProperty("description") String description
) implements WalletEvent {

    public DepositMade {
        if (depositId == null || depositId.trim().isEmpty()) {
            throw new IllegalArgumentException("Deposit ID cannot be null or empty");
        }
        if (walletId == null || walletId.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        if (newBalance < 0) {
            throw new IllegalArgumentException("New balance cannot be negative");
        }
        if (depositedAt == null) {
            throw new IllegalArgumentException("Deposited at timestamp cannot be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("Description cannot be null");
        }
    }

    /**
     * Create a DepositMade event.
     */
    public static DepositMade of(
            String depositId,
            String walletId,
            int amount,
            int newBalance,
            String description
    ) {
        return new DepositMade(
                depositId,
                walletId,
                amount,
                newBalance,
                Instant.now(),
                description
        );
    }

    @Override
    public String getEventType() {
        return "DepositMade";
    }

    @Override
    public Instant getOccurredAt() {
        return depositedAt;
    }
}
