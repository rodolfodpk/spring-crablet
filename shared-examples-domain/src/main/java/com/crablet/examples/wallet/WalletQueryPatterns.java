package com.crablet.examples.wallet;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.examples.wallet.event.*;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.wallet.WalletTags.*;

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
                .events(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class))
                .tag(WALLET_ID, walletId)
                .event(type(MoneyTransferred.class), FROM_WALLET_ID, walletId)
                .event(type(MoneyTransferred.class), TO_WALLET_ID, walletId)
                .build();
    }

    /**
     * Complete decision model query for transfer operations.
     * Includes all events affecting both source and destination wallets.
     */
    public static Query transferDecisionModel(String fromWalletId, String toWalletId) {
        return QueryBuilder.create()
                .events(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class))
                .tag(WALLET_ID, fromWalletId)
                .event(type(MoneyTransferred.class), FROM_WALLET_ID, fromWalletId)
                .event(type(MoneyTransferred.class), TO_WALLET_ID, fromWalletId)
                .events(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class))
                .tag(WALLET_ID, toWalletId)
                .event(type(MoneyTransferred.class), FROM_WALLET_ID, toWalletId)
                .event(type(MoneyTransferred.class), TO_WALLET_ID, toWalletId)
                .build();
    }

    /**
     * Period-aware decision model query for single wallet operations.
     * <p>
     * Queries only events from a specific period (year/month) by including period tags.
     * Used for projecting balance within a specific statement period.
     * <p>
     * Includes WalletStatementOpened to get opening balance for the period.
     *
     * @param walletId The wallet ID
     * @param year     The period year
     * @param month    The period month (1-12)
     * @return Query for events in the specified period
     */
    public static Query singleWalletPeriodDecisionModel(String walletId, int year, int month) {
        return QueryBuilder.create()
                // Include WalletOpened to establish wallet existence (no period tags)
                .event(type(WalletOpened.class), WALLET_ID, walletId)
                // Include WalletStatementOpened to get opening balance (all tags must match)
                .matching(
                        new String[]{type(WalletStatementOpened.class)},
                        QueryBuilder.tag(WALLET_ID, walletId),
                        QueryBuilder.tag(YEAR, String.valueOf(year)),
                        QueryBuilder.tag(MONTH, String.valueOf(month))
                )
                // Include transaction events for this period
                .events(type(DepositMade.class), type(WithdrawalMade.class))
                .tags(
                        QueryBuilder.tag(WALLET_ID, walletId),
                        QueryBuilder.tag(YEAR, String.valueOf(year)),
                        QueryBuilder.tag(MONTH, String.valueOf(month))
                )
                // Include transfers affecting this wallet in this period (as FROM wallet)
                .matching(
                        new String[]{type(MoneyTransferred.class)},
                        QueryBuilder.tag(FROM_WALLET_ID, walletId),
                        QueryBuilder.tag(FROM_YEAR, String.valueOf(year)),
                        QueryBuilder.tag(FROM_MONTH, String.valueOf(month))
                )
                // Include transfers affecting this wallet in this period (as TO wallet)
                .matching(
                        new String[]{type(MoneyTransferred.class)},
                        QueryBuilder.tag(TO_WALLET_ID, walletId),
                        QueryBuilder.tag(TO_YEAR, String.valueOf(year)),
                        QueryBuilder.tag(TO_MONTH, String.valueOf(month))
                )
                .build();
    }

    /**
     * Period-aware decision model query for single wallet operations (active period).
     * <p>
     * Queries events from the current active period using year/month tags.
     * Used by command handlers to project balance for the current period.
     * <p>
     * Includes WalletStatementOpened to get opening balance for the period.
     *
     * @param walletId The wallet ID
     * @param year     The period year
     * @param month    The period month (1-12)
     * @return Query for events in the specified period
     */
    public static Query singleWalletActivePeriodDecisionModel(String walletId, int year, int month) {
        return singleWalletPeriodDecisionModel(walletId, year, month);
    }

    /**
     * Period-aware decision model query for transfer operations.
     * <p>
     * Queries events from both wallets' active periods independently.
     * Includes WalletStatementOpened events for both wallets to get opening balances.
     * <p>
     * Wallets may be in different periods, so each wallet's events are filtered
     * by that wallet's period tags (year/month for from wallet, year/month for to wallet).
     *
     * @param fromWalletId The source wallet ID
     * @param fromYear     The source wallet's period year
     * @param fromMonth    The source wallet's period month (1-12)
     * @param toWalletId   The destination wallet ID
     * @param toYear       The destination wallet's period year
     * @param toMonth      The destination wallet's period month (1-12)
     * @return Query for events in both wallets' periods
     */
    public static Query transferPeriodDecisionModel(
            String fromWalletId, int fromYear, int fromMonth,
            String toWalletId, int toYear, int toMonth) {
        return QueryBuilder.create()
                // From wallet's WalletOpened (no period tags)
                .event(type(WalletOpened.class), WALLET_ID, fromWalletId)
                // From wallet's WalletStatementOpened
                .matching(
                        new String[]{type(WalletStatementOpened.class)},
                        QueryBuilder.tag(WALLET_ID, fromWalletId),
                        QueryBuilder.tag(YEAR, String.valueOf(fromYear)),
                        QueryBuilder.tag(MONTH, String.valueOf(fromMonth))
                )
                // From wallet's transaction events
                .events(type(DepositMade.class), type(WithdrawalMade.class))
                .tags(
                        QueryBuilder.tag(WALLET_ID, fromWalletId),
                        QueryBuilder.tag(YEAR, String.valueOf(fromYear)),
                        QueryBuilder.tag(MONTH, String.valueOf(fromMonth))
                )
                // To wallet's WalletOpened (no period tags)
                .event(type(WalletOpened.class), WALLET_ID, toWalletId)
                // To wallet's WalletStatementOpened
                .matching(
                        new String[]{type(WalletStatementOpened.class)},
                        QueryBuilder.tag(WALLET_ID, toWalletId),
                        QueryBuilder.tag(YEAR, String.valueOf(toYear)),
                        QueryBuilder.tag(MONTH, String.valueOf(toMonth))
                )
                // To wallet's transaction events
                .events(type(DepositMade.class), type(WithdrawalMade.class))
                .tags(
                        QueryBuilder.tag(WALLET_ID, toWalletId),
                        QueryBuilder.tag(YEAR, String.valueOf(toYear)),
                        QueryBuilder.tag(MONTH, String.valueOf(toMonth))
                )
                // Transfer events affecting from wallet (tagged with FROM_YEAR/FROM_MONTH)
                .matching(
                        new String[]{type(MoneyTransferred.class)},
                        QueryBuilder.tag(FROM_WALLET_ID, fromWalletId),
                        QueryBuilder.tag(FROM_YEAR, String.valueOf(fromYear)),
                        QueryBuilder.tag(FROM_MONTH, String.valueOf(fromMonth))
                )
                // Transfer events affecting to wallet (tagged with TO_YEAR/TO_MONTH)
                .matching(
                        new String[]{type(MoneyTransferred.class)},
                        QueryBuilder.tag(TO_WALLET_ID, toWalletId),
                        QueryBuilder.tag(TO_YEAR, String.valueOf(toYear)),
                        QueryBuilder.tag(TO_MONTH, String.valueOf(toMonth))
                )
                .build();
    }
}

