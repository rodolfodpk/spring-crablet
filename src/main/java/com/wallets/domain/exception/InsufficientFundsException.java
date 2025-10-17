package com.wallets.domain.exception;

/**
 * Exception thrown when attempting to withdraw or transfer more money than available in the wallet.
 */
public class InsufficientFundsException extends RuntimeException {
    
    private final String walletId;
    private final int currentBalance;
    private final int requestedAmount;
    
    public InsufficientFundsException(String walletId, int currentBalance, int requestedAmount) {
        super(String.format("Insufficient funds in wallet %s: balance %d, requested %d", 
                           walletId, currentBalance, requestedAmount));
        this.walletId = walletId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
    
    public InsufficientFundsException(String walletId, int currentBalance, int requestedAmount, String message) {
        super(message);
        this.walletId = walletId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
    
    public String getWalletId() {
        return walletId;
    }
    
    public int getCurrentBalance() {
        return currentBalance;
    }
    
    public int getRequestedAmount() {
        return requestedAmount;
    }
}
