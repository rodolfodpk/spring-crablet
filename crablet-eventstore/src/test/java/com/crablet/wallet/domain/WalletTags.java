package com.crablet.wallet.domain;

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
    
}
