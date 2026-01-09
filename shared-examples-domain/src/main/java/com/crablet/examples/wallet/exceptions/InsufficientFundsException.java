package com.crablet.examples.wallet.exceptions;

/**
 * Exception thrown when attempting to withdraw or transfer more money than available in the wallet.
 */
public class InsufficientFundsException extends RuntimeException {

    public final String walletId;
    public final int currentBalance;
    public final int requestedAmount;

    public InsufficientFundsException(String walletId, int currentBalance, int requestedAmount) {
        super(String.format("Insufficient funds in wallet %s: balance %d, requested %d",
                walletId, currentBalance, requestedAmount));
        this.walletId = walletId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }
}

