package com.crablet.examples.wallet.commands;

import com.crablet.examples.wallet.WalletCommand;

public record CloseWalletCommand(String walletId) implements WalletCommand {

    public CloseWalletCommand {
        if (walletId == null || walletId.isBlank()) {
            throw new IllegalArgumentException("walletId must not be blank");
        }
    }

    public static CloseWalletCommand of(String walletId) {
        return new CloseWalletCommand(walletId);
    }

    @Override
    public String getWalletId() {
        return walletId;
    }
}
