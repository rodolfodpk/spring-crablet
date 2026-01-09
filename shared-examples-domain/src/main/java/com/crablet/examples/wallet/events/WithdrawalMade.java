package com.crablet.examples.wallet.events;

import am.ik.yavi.arguments.Arguments5Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
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

    private static Arguments5Validator<String, String, Integer, Integer, String, WithdrawalMade> validator =
            Yavi.arguments()
                    ._string("withdrawalId", c -> c.notNull().notBlank())
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._integer("amount", c -> c.greaterThan(0))
                    ._integer("newBalance", c -> c.greaterThanOrEqual(0))
                    ._string("description", c -> c.notNull().notBlank())
                    .apply((withdrawalId, walletId, amount, newBalance, description) -> 
                            new WithdrawalMade(withdrawalId, walletId, amount, newBalance, null, description));

    public WithdrawalMade {
        try {
            validator.lazy().validated(withdrawalId, walletId, amount, newBalance, description);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid WithdrawalMade: " + e.getMessage(), e);
        }
        if (withdrawnAt == null) {
            throw new IllegalArgumentException("withdrawnAt must not be null");
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
}

