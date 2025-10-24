package com.wallets.domain;

import com.crablet.core.Query;
import com.crablet.core.QueryBuilder;

/**
 * Reusable query patterns for wallet operations.
 * Encapsulates DCB decision model queries for wallet domain.
 */
public class WalletQueryPatterns {

    /**
     * Complete decision model query for single wallet operations.
     * Used by Deposit and Withdraw handlers.
     */
    public static Query singleWalletDecisionModel(String walletId) {
        return QueryBuilder.create()
                .events(WalletEventTypes.WALLET_OPENED, WalletEventTypes.DEPOSIT_MADE, WalletEventTypes.WITHDRAWAL_MADE).tag(WalletTags.WALLET_ID, walletId)
                .event(WalletEventTypes.MONEY_TRANSFERRED, WalletTags.FROM_WALLET_ID, walletId)
                .event(WalletEventTypes.MONEY_TRANSFERRED, WalletTags.TO_WALLET_ID, walletId)
                .build();
    }

    /**
     * Complete decision model query for transfer operations.
     * Includes all events affecting both source and destination wallets.
     */
    public static Query transferDecisionModel(String fromWalletId, String toWalletId) {
        return QueryBuilder.create()
                .events(WalletEventTypes.WALLET_OPENED, WalletEventTypes.DEPOSIT_MADE, WalletEventTypes.WITHDRAWAL_MADE).tag(WalletTags.WALLET_ID, fromWalletId)
                .event(WalletEventTypes.MONEY_TRANSFERRED, WalletTags.FROM_WALLET_ID, fromWalletId)
                .event(WalletEventTypes.MONEY_TRANSFERRED, WalletTags.TO_WALLET_ID, fromWalletId)
                .events(WalletEventTypes.WALLET_OPENED, WalletEventTypes.DEPOSIT_MADE, WalletEventTypes.WITHDRAWAL_MADE).tag(WalletTags.WALLET_ID, toWalletId)
                .event(WalletEventTypes.MONEY_TRANSFERRED, WalletTags.FROM_WALLET_ID, toWalletId)
                .event(WalletEventTypes.MONEY_TRANSFERRED, WalletTags.TO_WALLET_ID, toWalletId)
                .build();
    }

    /**
     * Simple query for wallet existence check.
     * Used by OpenWallet handler.
     */
    public static Query walletExistenceQuery(String walletId) {
        return QueryBuilder.create()
                .event(WalletEventTypes.WALLET_OPENED, WalletTags.WALLET_ID, walletId)
                .build();
    }
}

