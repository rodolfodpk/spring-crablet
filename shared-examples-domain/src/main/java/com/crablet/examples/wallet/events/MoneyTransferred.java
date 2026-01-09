package com.crablet.examples.wallet.events;

import am.ik.yavi.arguments.Arguments7Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
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

    private static Arguments7Validator<String, String, String, Integer, Integer, Integer, String, MoneyTransferred> validator =
            Yavi.arguments()
                    ._string("transferId", c -> c.notNull().notBlank())
                    ._string("fromWalletId", c -> c.notNull().notBlank())
                    ._string("toWalletId", c -> c.notNull().notBlank())
                    ._integer("amount", c -> c.greaterThan(0))
                    ._integer("fromBalance", c -> c.greaterThanOrEqual(0))
                    ._integer("toBalance", c -> c.greaterThanOrEqual(0))
                    ._string("description", c -> c.notNull().notBlank())
                    .apply((transferId, fromWalletId, toWalletId, amount, fromBalance, toBalance, description) -> 
                            new MoneyTransferred(transferId, fromWalletId, toWalletId, amount, fromBalance, toBalance, null, description));

    public MoneyTransferred {
        try {
            validator.lazy().validated(transferId, fromWalletId, toWalletId, amount, fromBalance, toBalance, description);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid MoneyTransferred: " + e.getMessage(), e);
        }
        if (transferredAt == null) {
            throw new IllegalArgumentException("transferredAt must not be null");
        }

        // Cross-field validation: cannot transfer to the same wallet
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
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
}

