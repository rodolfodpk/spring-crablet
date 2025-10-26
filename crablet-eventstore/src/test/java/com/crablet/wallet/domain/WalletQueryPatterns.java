package com.crablet.wallet.domain;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;

import static com.crablet.wallet.domain.WalletEventTypes.*;
import static com.crablet.wallet.domain.WalletTags.*;

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
                .events(WALLET_OPENED, DEPOSIT_MADE, WITHDRAWAL_MADE)
                .tag(WALLET_ID, walletId)
                .event(MONEY_TRANSFERRED, FROM_WALLET_ID, walletId)
                .event(MONEY_TRANSFERRED, TO_WALLET_ID, walletId)
                .build();
    }

    /**
     * Complete decision model query for transfer operations.
     * Includes all events affecting both source and destination wallets.
     */
    public static Query transferDecisionModel(String fromWalletId, String toWalletId) {
        return QueryBuilder.create()
                .events(WALLET_OPENED, DEPOSIT_MADE, WITHDRAWAL_MADE)
                .tag(WALLET_ID, fromWalletId)
                .event(MONEY_TRANSFERRED, FROM_WALLET_ID, fromWalletId)
                .event(MONEY_TRANSFERRED, TO_WALLET_ID, fromWalletId)
                .events(WALLET_OPENED, DEPOSIT_MADE, WITHDRAWAL_MADE)
                .tag(WALLET_ID, toWalletId)
                .event(MONEY_TRANSFERRED, FROM_WALLET_ID, toWalletId)
                .event(MONEY_TRANSFERRED, TO_WALLET_ID, toWalletId)
                .build();
    }

    /**
     * Simple query for wallet existence check.
     * Used by OpenWallet handler.
     */
    public static Query walletExistenceQuery(String walletId) {
        return QueryBuilder.create()
                .event(WALLET_OPENED, WALLET_ID, walletId)
                .build();
    }
}

