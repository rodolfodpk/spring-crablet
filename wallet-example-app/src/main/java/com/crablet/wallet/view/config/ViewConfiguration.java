package com.crablet.wallet.view.config;

import com.crablet.views.config.ViewSubscriptionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for view subscriptions.
 * Defines which events each view subscribes to.
 */
@Configuration
public class ViewConfiguration {

    /**
     * Wallet balance view subscription.
     * Subscribes to all wallet events that affect balance.
     */
    @Bean
    public ViewSubscriptionConfig walletBalanceViewSubscription() {
        return ViewSubscriptionConfig.builder("wallet-balance-view")
                .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred")
                .anyOfTags("wallet_id", "from_wallet_id", "to_wallet_id")
                .build();
    }

    /**
     * Wallet transaction view subscription.
     * Subscribes to transaction events (deposits, withdrawals, transfers).
     */
    @Bean
    public ViewSubscriptionConfig walletTransactionViewSubscription() {
        return ViewSubscriptionConfig.builder("wallet-transaction-view")
                .eventTypes("DepositMade", "WithdrawalMade", "MoneyTransferred")
                .anyOfTags("wallet_id", "from_wallet_id", "to_wallet_id")
                .build();
    }

    /**
     * Wallet summary view subscription.
     * Subscribes to all wallet events for aggregated statistics.
     */
    @Bean
    public ViewSubscriptionConfig walletSummaryViewSubscription() {
        return ViewSubscriptionConfig.builder("wallet-summary-view")
                .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred")
                .anyOfTags("wallet_id", "from_wallet_id", "to_wallet_id")
                .build();
    }

    /**
     * Wallet statement view subscription.
     * Subscribes to statement events and transaction events for period tracking.
     */
    @Bean
    public ViewSubscriptionConfig walletStatementViewSubscription() {
        return ViewSubscriptionConfig.builder("wallet-statement-view")
                .eventTypes(
                    "WalletStatementOpened",
                    "WalletStatementClosed",
                    "DepositMade",
                    "WithdrawalMade",
                    "MoneyTransferred"
                )
                .anyOfTags("wallet_id", "from_wallet_id", "to_wallet_id")
                .build();
    }
}

