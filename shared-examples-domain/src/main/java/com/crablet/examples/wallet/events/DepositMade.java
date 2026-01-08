package com.crablet.examples.wallet.events;

import am.ik.yavi.arguments.Arguments5Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
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

    private static Arguments5Validator<String, String, Integer, Integer, String, DepositMade> validator =
            Yavi.arguments()
                    ._string("depositId", c -> c.notNull().notBlank())
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._integer("amount", c -> c.greaterThan(0))
                    ._integer("newBalance", c -> c.greaterThanOrEqual(0))
                    ._string("description", c -> c.notNull().notBlank())
                    .apply((depositId, walletId, amount, newBalance, description) -> 
                            new DepositMade(depositId, walletId, amount, newBalance, null, description));

    public DepositMade {
        try {
            validator.lazy().validated(depositId, walletId, amount, newBalance, description);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid DepositMade: " + e.getMessage(), e);
        }
        if (depositedAt == null) {
            throw new IllegalArgumentException("depositedAt must not be null");
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
}

