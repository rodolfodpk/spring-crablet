package com.crablet.examples.wallet.events;

import am.ik.yavi.arguments.Arguments4Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * WalletStatementOpened represents when a new wallet statement period is opened.
 * <p>
 * This event marks the start of a new statement period and contains the opening balance
 * for that period. Used in the closing the books pattern.
 */
public record WalletStatementOpened(
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("statement_id") String statementId,
        @JsonProperty("year") int year,
        @JsonProperty("month") Integer month,
        @JsonProperty("day") Integer day,
        @JsonProperty("hour") Integer hour,
        @JsonProperty("opening_balance") int openingBalance,
        @JsonProperty("opened_at") Instant openedAt
) implements WalletEvent {

    private static Arguments4Validator<String, String, Integer, Integer, WalletStatementOpened> validator =
            Yavi.arguments()
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._string("statementId", c -> c.notNull().notBlank())
                    ._integer("year", c -> c.greaterThan(0))
                    ._integer("openingBalance", c -> c.greaterThanOrEqual(0))
                    .apply((walletId, statementId, year, openingBalance) -> 
                            new WalletStatementOpened(walletId, statementId, year, null, null, null, openingBalance, null));

    public WalletStatementOpened {
        try {
            validator.lazy().validated(walletId, statementId, year, openingBalance);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid WalletStatementOpened: " + e.getMessage(), e);
        }
        
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt must not be null");
        }
        // Manual validation for nullable Integer fields
        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("month must be null or between 1 and 12");
        }
        if (day != null && (day < 1 || day > 31)) {
            throw new IllegalArgumentException("day must be null or between 1 and 31");
        }
        if (hour != null && (hour < 0 || hour > 23)) {
            throw new IllegalArgumentException("hour must be null or between 0 and 23");
        }
    }

    /**
     * Create a WalletStatementOpened event.
     *
     * @param walletId       The wallet ID
     * @param statementId    The statement ID (format: wallet:{walletId}:{year}-{month}...)
     * @param year           The year
     * @param month          The month (1-12) or null for yearly periods
     * @param day            The day (1-31) or null for monthly/yearly periods
     * @param hour           The hour (0-23) or null for daily/monthly/yearly periods
     * @param openingBalance The opening balance for this period
     * @return WalletStatementOpened event
     */
    public static WalletStatementOpened of(
            String walletId,
            String statementId,
            int year,
            Integer month,
            Integer day,
            Integer hour,
            int openingBalance
    ) {
        return new WalletStatementOpened(
                walletId,
                statementId,
                year,
                month,
                day,
                hour,
                openingBalance,
                Instant.now()
        );
    }
}

