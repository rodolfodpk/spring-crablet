package com.wallets.domain;

import com.crablet.core.Query;
import com.crablet.core.QueryBuilder;
import com.crablet.core.QueryItem;
import com.crablet.core.Tag;

import java.util.List;

/**
 * Reusable query patterns for wallet operations.
 * Encapsulates DCB decision model queries for wallet domain.
 */
public class WalletQueryPatterns {
    
    /**
     * Query for wallet balance events (WalletOpened, Deposits, Withdrawals).
     */
    public static List<QueryItem> walletBalanceEvents(String walletId) {
        return List.of(
            QueryItem.of(
                List.of("WalletOpened", "DepositMade", "WithdrawalMade"),
                List.of(new Tag("wallet_id", walletId))
            )
        );
    }
    
    /**
     * Query for transfers affecting a wallet (both from and to).
     */
    public static List<QueryItem> walletTransfers(String walletId) {
        return List.of(
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("from_wallet_id", walletId))
            ),
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("to_wallet_id", walletId))
            )
        );
    }
    
    /**
     * Complete decision model query for single wallet operations.
     * Used by Deposit and Withdraw handlers.
     */
    public static Query singleWalletDecisionModel(String walletId) {
        return QueryBuilder.create()
            .matching(walletBalanceEvents(walletId))
            .matching(walletTransfers(walletId))
            .build();
    }
    
    /**
     * Complete decision model query for transfer operations.
     * Includes all events affecting both source and destination wallets.
     */
    public static Query transferDecisionModel(String fromWalletId, String toWalletId) {
        return QueryBuilder.create()
            .matching(walletBalanceEvents(fromWalletId))
            .matching(walletTransfers(fromWalletId))
            .matching(walletBalanceEvents(toWalletId))
            .matching(walletTransfers(toWalletId))
            .build();
    }
    
    /**
     * Simple query for wallet existence check.
     * Used by OpenWallet handler.
     */
    public static Query walletExistenceQuery(String walletId) {
        return Query.of(QueryItem.of(
            List.of("WalletOpened"),
            List.of(new Tag("wallet_id", walletId))
        ));
    }
}

