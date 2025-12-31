package com.crablet.examples.wallet.exception;

/**
 * Exception thrown when attempting to create a wallet that already exists.
 * This is handled as an idempotent operation (200 OK) rather than an error.
 */
public class WalletAlreadyExistsException extends RuntimeException {

    public final String walletId;

    public WalletAlreadyExistsException(String walletId) {
        super("Wallet already exists: " + walletId);
        this.walletId = walletId;
    }
}

