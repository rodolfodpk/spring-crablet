package com.crablet.examples.wallet.features.transfer;

import com.crablet.examples.wallet.domain.projections.WalletBalanceState;

/**
 * State for transfer operations - balances for both wallets.
 */
public record TransferState(
    WalletBalanceState fromWallet,
    WalletBalanceState toWallet
) {}

