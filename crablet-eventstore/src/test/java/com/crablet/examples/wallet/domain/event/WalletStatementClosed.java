package com.crablet.examples.wallet.domain.event;

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

    public WalletStatementClosed {
        if (walletId == null || walletId.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID cannot be null or empty");
        }
        if (statementId == null || statementId.trim().isEmpty()) {
            throw new IllegalArgumentException("Statement ID cannot be null or empty");
        }
        if (year < 1) {
            throw new IllegalArgumentException("Year must be positive");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (day != null && (day < 1 || day > 31)) {
            throw new IllegalArgumentException("Day must be between 1 and 31");
        }
        if (hour != null && (hour < 0 || hour > 23)) {
            throw new IllegalArgumentException("Hour must be between 0 and 23");
        }
        if (closedAt == null) {
            throw new IllegalArgumentException("Closed at timestamp cannot be null");
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

