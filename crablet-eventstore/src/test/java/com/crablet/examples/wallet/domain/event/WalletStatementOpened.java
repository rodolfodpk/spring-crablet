package com.crablet.examples.wallet.domain.event;

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

    public WalletStatementOpened {
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
        if (openedAt == null) {
            throw new IllegalArgumentException("Opened at timestamp cannot be null");
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

