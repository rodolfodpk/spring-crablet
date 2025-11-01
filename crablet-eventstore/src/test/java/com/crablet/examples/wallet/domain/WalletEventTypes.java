package com.crablet.examples.wallet.domain;

/**
 * Constants for wallet domain event type names.
 * 
 * This class provides centralized event type constants to:
 * - Prevent typos in event type names
 * - Make event types easily discoverable
 * - Enable refactoring of event names if needed
 * - Improve code readability and maintainability
 */
public final class WalletEventTypes {
    
    // Private constructor to prevent instantiation
    private WalletEventTypes() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Event type for wallet opening.
     */
    public static final String WALLET_OPENED = "WalletOpened";
    
    /**
     * Event type for money transfers between wallets.
     */
    public static final String MONEY_TRANSFERRED = "MoneyTransferred";
    
    /**
     * Event type for deposits to a wallet.
     */
    public static final String DEPOSIT_MADE = "DepositMade";
    
    /**
     * Event type for withdrawals from a wallet.
     */
    public static final String WITHDRAWAL_MADE = "WithdrawalMade";
}

