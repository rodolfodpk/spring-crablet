package com.crablet.examples.wallet.events;

import am.ik.yavi.arguments.Arguments3Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
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

    private static Arguments3Validator<String, String, Integer, WalletOpened> validator =
            Yavi.arguments()
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._string("owner", c -> c.notNull().notBlank())
                    ._integer("initialBalance", c -> c.greaterThanOrEqual(0))
                    .apply((walletId, owner, initialBalance) -> 
                            new WalletOpened(walletId, owner, initialBalance, null));

    public WalletOpened {
        try {
            validator.lazy().validated(walletId, owner, initialBalance);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid WalletOpened: " + e.getMessage(), e);
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt must not be null");
        }
    }

    /**
     * Create a WalletOpened event.
     */
    public static WalletOpened of(String walletId, String owner, int initialBalance) {
        return new WalletOpened(walletId, owner, initialBalance, Instant.now());
    }
}

