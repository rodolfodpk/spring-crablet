package com.crablet.examples.wallet.period;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Record representing a wallet statement period identifier.
 * <p>
 * Used to identify a specific statement period for a wallet.
 * Supports different period types (hourly, daily, monthly, yearly).
 */
public record WalletStatementId(
        String walletId,
        int year,
        Integer month,  // Nullable for yearly periods
        Integer day,    // Nullable for monthly/yearly periods
        Integer hour    // Nullable for daily/monthly/yearly periods
) {
    public WalletStatementId {
        if (walletId == null || walletId.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID cannot be null or empty");
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
        // Enforce consistency: if hour is set, day must be set
        if (hour != null && day == null) {
            throw new IllegalArgumentException("Cannot specify hour without day");
        }
        // Enforce consistency: if day is set, month must be set
        if (day != null && month == null) {
            throw new IllegalArgumentException("Cannot specify day without month");
        }
    }

    /**
     * Create a yearly period identifier.
     *
     * @param walletId The wallet ID
     * @param year     The year
     * @return WalletStatementId for yearly period
     */
    public static WalletStatementId ofYearly(String walletId, int year) {
        return new WalletStatementId(walletId, year, null, null, null);
    }

    /**
     * Create a monthly period identifier.
     *
     * @param walletId The wallet ID
     * @param year     The year
     * @param month    The month (1-12)
     * @return WalletStatementId for monthly period
     */
    public static WalletStatementId ofMonthly(String walletId, int year, int month) {
        return new WalletStatementId(walletId, year, month, null, null);
    }

    /**
     * Create a daily period identifier.
     *
     * @param walletId The wallet ID
     * @param year     The year
     * @param month    The month (1-12)
     * @param day      The day (1-31)
     * @return WalletStatementId for daily period
     */
    public static WalletStatementId ofDaily(String walletId, int year, int month, int day) {
        return new WalletStatementId(walletId, year, month, day, null);
    }

    /**
     * Create an hourly period identifier.
     *
     * @param walletId The wallet ID
     * @param year     The year
     * @param month    The month (1-12)
     * @param day      The day (1-31)
     * @param hour     The hour (0-23)
     * @return WalletStatementId for hourly period
     */
    public static WalletStatementId ofHourly(String walletId, int year, int month, int day, int hour) {
        return new WalletStatementId(walletId, year, month, day, hour);
    }

    /**
     * Create a monthly period identifier from YearMonth.
     *
     * @param walletId  The wallet ID
     * @param yearMonth The YearMonth
     * @return WalletStatementId for monthly period
     */
    public static WalletStatementId fromYearMonth(String walletId, YearMonth yearMonth) {
        return new WalletStatementId(walletId, yearMonth.getYear(), yearMonth.getMonthValue(), null, null);
    }

    /**
     * Create a period identifier from Instant and period type.
     *
     * @param walletId   The wallet ID
     * @param instant    The instant to extract period from
     * @param periodType The period type
     * @return WalletStatementId for the period containing the instant
     */
    public static WalletStatementId fromInstant(String walletId, Instant instant, PeriodType periodType) {
        LocalDateTime dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        return switch (periodType) {
            case HOURLY -> new WalletStatementId(
                    walletId,
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    dateTime.getHour()
            );
            case DAILY -> new WalletStatementId(
                    walletId,
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    null
            );
            case MONTHLY -> new WalletStatementId(
                    walletId,
                    dateTime.getYear(),
                    dateTime.getMonthValue(),
                    null,
                    null
            );
            case YEARLY -> new WalletStatementId(
                    walletId,
                    dateTime.getYear(),
                    null,
                    null,
                    null
            );
            case NONE -> throw new IllegalArgumentException("Cannot create period ID for NONE period type");
        };
    }

    /**
     * Create a period identifier from LocalDate and period type.
     *
     * @param walletId   The wallet ID
     * @param date       The date to extract period from
     * @param periodType The period type
     * @return WalletStatementId for the period containing the date
     */
    public static WalletStatementId from(String walletId, LocalDate date, PeriodType periodType) {
        return switch (periodType) {
            case HOURLY -> throw new IllegalArgumentException("Hourly period requires LocalDateTime, not LocalDate");
            case DAILY -> ofDaily(walletId, date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            case MONTHLY -> ofMonthly(walletId, date.getYear(), date.getMonthValue());
            case YEARLY -> ofYearly(walletId, date.getYear());
            case NONE -> throw new IllegalArgumentException("Cannot create period ID for NONE period type");
        };
    }

    /**
     * Convert to stream ID format.
     * <p>
     * Format:
     * - Hourly: wallet:{walletId}:{year}-{month}-{day}-{hour}
     * - Daily: wallet:{walletId}:{year}-{month}-{day}
     * - Monthly: wallet:{walletId}:{year}-{month}
     * - Yearly: wallet:{walletId}:{year}
     *
     * @return Stream ID string
     */
    public String toStreamId() {
        return switch (periodType()) {
            case HOURLY -> "wallet:%s:%d-%02d-%02d-%02d".formatted(walletId, year, month, day, hour);
            case DAILY -> "wallet:%s:%d-%02d-%02d".formatted(walletId, year, month, day);
            case MONTHLY -> "wallet:%s:%d-%02d".formatted(walletId, year, month);
            case YEARLY -> "wallet:%s:%d".formatted(walletId, year);
            case NONE -> throw new IllegalStateException("Cannot create stream ID for NONE period type");
        };
    }

    /**
     * Determine the period type based on the fields.
     *
     * @return The period type
     */
    private PeriodType periodType() {
        if (hour != null) {
            return PeriodType.HOURLY;
        } else if (day != null) {
            return PeriodType.DAILY;
        } else if (month != null) {
            return PeriodType.MONTHLY;
        } else {
            return PeriodType.YEARLY;
        }
    }

    /**
     * Get YearMonth for monthly periods.
     *
     * @return YearMonth for this period
     * @throws IllegalStateException if not a monthly period
     */
    public YearMonth toYearMonth() {
        if (month == null) {
            throw new IllegalStateException("Not a monthly period");
        }
        return YearMonth.of(year, month);
    }
}

