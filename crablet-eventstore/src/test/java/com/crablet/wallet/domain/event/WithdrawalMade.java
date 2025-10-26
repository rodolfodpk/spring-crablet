package com.crablet.wallet.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * WithdrawalMade represents when money is withdrawn from a wallet.
 * This is the event data structure.
 */
public record WithdrawalMade(
        @JsonProperty("withdrawal_id") String withdrawalId,
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("amount") int amount,
        @JsonProperty("new_balance") int newBalance,
        @JsonProperty("withdrawn_at") Instant withdrawnAt,
        @JsonProperty("description") String description
) implements WalletEvent {

    public WithdrawalMade {
        if (withdrawalId == null || withdrawalId.trim().isEmpty()) {
            throw new IllegalArgumentException("Withdrawal ID cannot be null or empty");
        }
        if (walletId == null || walletId.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        if (newBalance < 0) {
            throw new IllegalArgumentException("New balance cannot be negative");
        }
        if (withdrawnAt == null) {
            throw new IllegalArgumentException("Withdrawn at timestamp cannot be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("Description cannot be null");
        }
    }

    /**
     * Create a WithdrawalMade event.
     */
    public static WithdrawalMade of(
            String withdrawalId,
            String walletId,
            int amount,
            int newBalance,
            String description
    ) {
        return new WithdrawalMade(
                withdrawalId,
                walletId,
                amount,
                newBalance,
                Instant.now(),
                description
        );
    }

    @Override
    public String getEventType() {
        return "WithdrawalMade";
    }

    @Override
    public Instant getOccurredAt() {
        return withdrawnAt;
    }
}
