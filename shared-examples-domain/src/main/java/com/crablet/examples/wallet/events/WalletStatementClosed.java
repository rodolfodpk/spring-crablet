package com.crablet.examples.wallet.events;

import am.ik.yavi.arguments.Arguments5Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * WalletStatementClosed represents when a wallet statement period is closed.
 * <p>
 * This event marks the end of a statement period and contains summary information
 * including opening balance, closing balance, and transaction totals.
 * Used in the closing the books pattern.
 */
public record WalletStatementClosed(
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("statement_id") String statementId,
        @JsonProperty("year") int year,
        @JsonProperty("month") Integer month,
        @JsonProperty("day") Integer day,
        @JsonProperty("hour") Integer hour,
        @JsonProperty("opening_balance") int openingBalance,
        @JsonProperty("closing_balance") int closingBalance,
        @JsonProperty("closed_at") Instant closedAt
) implements WalletEvent {

    private static Arguments5Validator<String, String, Integer, Integer, Integer, WalletStatementClosed> validator =
            Yavi.arguments()
                    ._string("walletId", c -> c.notNull().notBlank())
                    ._string("statementId", c -> c.notNull().notBlank())
                    ._integer("year", c -> c.greaterThan(0))
                    ._integer("openingBalance", c -> c.greaterThanOrEqual(0))
                    ._integer("closingBalance", c -> c.greaterThanOrEqual(0))
                    .apply((walletId, statementId, year, openingBalance, closingBalance) -> 
                            new WalletStatementClosed(walletId, statementId, year, null, null, null, openingBalance, closingBalance, null));

    public WalletStatementClosed {
        try {
            validator.lazy().validated(walletId, statementId, year, openingBalance, closingBalance);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid WalletStatementClosed: " + e.getMessage(), e);
        }
        
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt must not be null");
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
     * Create a WalletStatementClosed event.
     *
     * @param walletId       The wallet ID
     * @param statementId    The statement ID (format: wallet:{walletId}:{year}-{month}...)
     * @param year           The year
     * @param month          The month (1-12) or null for yearly periods
     * @param day            The day (1-31) or null for monthly/yearly periods
     * @param hour           The hour (0-23) or null for daily/monthly/yearly periods
     * @param openingBalance The opening balance for this period
     * @param closingBalance The closing balance for this period
     * @return WalletStatementClosed event
     */
    public static WalletStatementClosed of(
            String walletId,
            String statementId,
            int year,
            Integer month,
            Integer day,
            Integer hour,
            int openingBalance,
            int closingBalance
    ) {
        return new WalletStatementClosed(
                walletId,
                statementId,
                year,
                month,
                day,
                hour,
                openingBalance,
                closingBalance,
                Instant.now()
        );
    }
}

