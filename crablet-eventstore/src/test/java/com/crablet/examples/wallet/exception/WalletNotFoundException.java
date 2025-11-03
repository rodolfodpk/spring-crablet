package com.crablet.examples.wallet.domain.exception;

/**
 * Exception thrown when attempting to perform operations on a wallet that does not exist.
 */
public class WalletNotFoundException extends RuntimeException {

    public final String walletId;

    public WalletNotFoundException(String walletId) {
        super("Wallet not found: " + walletId);
        this.walletId = walletId;
    }
}
