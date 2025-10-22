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
                .events("WalletOpened", "DepositMade", "WithdrawalMade").tag(WalletTags.WALLET_ID, walletId)
                .event("MoneyTransferred", WalletTags.FROM_WALLET_ID, walletId)
                .event("MoneyTransferred", WalletTags.TO_WALLET_ID, walletId)
                .build();
    }

    /**
     * Complete decision model query for transfer operations.
     * Includes all events affecting both source and destination wallets.
     */
    public static Query transferDecisionModel(String fromWalletId, String toWalletId) {
        return QueryBuilder.create()
                .events("WalletOpened", "DepositMade", "WithdrawalMade").tag(WalletTags.WALLET_ID, fromWalletId)
                .event("MoneyTransferred", WalletTags.FROM_WALLET_ID, fromWalletId)
                .event("MoneyTransferred", WalletTags.TO_WALLET_ID, fromWalletId)
                .events("WalletOpened", "DepositMade", "WithdrawalMade").tag(WalletTags.WALLET_ID, toWalletId)
                .event("MoneyTransferred", WalletTags.FROM_WALLET_ID, toWalletId)
                .event("MoneyTransferred", WalletTags.TO_WALLET_ID, toWalletId)
                .build();
    }

    /**
     * Simple query for wallet existence check.
     * Used by OpenWallet handler.
     */
    public static Query walletExistenceQuery(String walletId) {
        return QueryBuilder.create()
                .event("WalletOpened", WalletTags.WALLET_ID, walletId)
                .build();
    }
}

