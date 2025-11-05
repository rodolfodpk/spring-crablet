package com.crablet.examples.wallet;

/**
 * Constants for wallet domain tag names.
 * 
 * This class provides centralized tag name constants to:
 * - Prevent typos in tag names
 * - Make tag names easily discoverable
 * - Enable refactoring of tag names if needed
 * - Improve code readability and maintainability
 */
public final class WalletTags {
    
    // Private constructor to prevent instantiation
    private WalletTags() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Tag for wallet ID - used across all wallet operations.
     * This is the primary identifier for wallet-related events.
     */
    public static final String WALLET_ID = "wallet_id";
    
    /**
     * Tag for deposit operation ID - used for idempotency in deposit operations.
     */
    public static final String DEPOSIT_ID = "deposit_id";
    
    /**
     * Tag for withdrawal operation ID - used for idempotency in withdrawal operations.
     */
    public static final String WITHDRAWAL_ID = "withdrawal_id";
    
    /**
     * Tag for transfer operation ID - used for idempotency in transfer operations.
     */
    public static final String TRANSFER_ID = "transfer_id";
    
    /**
     * Tag for source wallet ID in transfer operations.
     * Used to identify the wallet money is transferred from.
     */
    public static final String FROM_WALLET_ID = "from_wallet_id";
    
    /**
     * Tag for destination wallet ID in transfer operations.
     * Used to identify the wallet money is transferred to.
     */
    public static final String TO_WALLET_ID = "to_wallet_id";
    
    /**
     * Tag for period year - used for closing the books pattern.
     * Identifies the year of the statement period.
     */
    public static final String YEAR = "year";
    
    /**
     * Tag for period month - used for closing the books pattern.
     * Identifies the month of the statement period.
     */
    public static final String MONTH = "month";
    
    /**
     * Tag for period day - used for daily statement periods.
     * Identifies the day of the statement period.
     */
    public static final String DAY = "day";
    
    /**
     * Tag for period hour - used for hourly statement periods.
     * Identifies the hour of the statement period (0-23).
     */
    public static final String HOUR = "hour";
    
    /**
     * Tag for period year of the source wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String FROM_YEAR = "from_year";
    
    /**
     * Tag for period month of the source wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String FROM_MONTH = "from_month";
    
    /**
     * Tag for period day of the source wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String FROM_DAY = "from_day";
    
    /**
     * Tag for period hour of the source wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String FROM_HOUR = "from_hour";
    
    /**
     * Tag for period year of the destination wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String TO_YEAR = "to_year";
    
    /**
     * Tag for period month of the destination wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String TO_MONTH = "to_month";
    
    /**
     * Tag for period day of the destination wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String TO_DAY = "to_day";
    
    /**
     * Tag for period hour of the destination wallet in transfer operations.
     * Used when wallets are in different periods.
     */
    public static final String TO_HOUR = "to_hour";
    
    /**
     * Tag for statement ID - used to identify wallet statement periods.
     * Format: wallet:{walletId}:{year}-{month} for monthly periods.
     */
    public static final String STATEMENT_ID = "statement_id";
    
}

