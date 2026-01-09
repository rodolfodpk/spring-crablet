package com.crablet.examples.wallet.projections;

/**
 * State for transfer operations - balances for both wallets.
 */
public record TransferState(
    WalletBalanceState fromWallet,
    WalletBalanceState toWallet
) {}

