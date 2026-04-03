package com.crablet.wallet.view.config;

import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.views.config.ViewSubscriptionConfig;
import com.crablet.wallet.view.projectors.WalletBalanceViewProjector;
import com.crablet.wallet.view.projectors.WalletStatementViewProjector;
import com.crablet.wallet.view.projectors.WalletSummaryViewProjector;
import com.crablet.wallet.view.projectors.WalletTransactionViewProjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.crablet.eventstore.store.EventType.type;

/**
 * Configuration for view subscriptions.
 * Defines which events each view subscribes to.
 */
@Configuration
public class ViewConfiguration {

    private static final String[] WALLET_ANY_OF_TAGS = {"wallet_id", "from_wallet_id", "to_wallet_id"};

    @Bean
    public ViewSubscriptionConfig walletBalanceViewSubscription(WalletBalanceViewProjector projector) {
        return ViewSubscriptionConfig.builder(projector.getViewName())
                .eventTypes(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class), type(MoneyTransferred.class))
                .anyOfTags(WALLET_ANY_OF_TAGS)
                .build();
    }

    @Bean
    public ViewSubscriptionConfig walletTransactionViewSubscription(WalletTransactionViewProjector projector) {
        return ViewSubscriptionConfig.builder(projector.getViewName())
                .eventTypes(type(DepositMade.class), type(WithdrawalMade.class), type(MoneyTransferred.class))
                .anyOfTags(WALLET_ANY_OF_TAGS)
                .build();
    }

    @Bean
    public ViewSubscriptionConfig walletSummaryViewSubscription(WalletSummaryViewProjector projector) {
        return ViewSubscriptionConfig.builder(projector.getViewName())
                .eventTypes(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class), type(MoneyTransferred.class))
                .anyOfTags(WALLET_ANY_OF_TAGS)
                .build();
    }

    @Bean
    public ViewSubscriptionConfig walletStatementViewSubscription(WalletStatementViewProjector projector) {
        return ViewSubscriptionConfig.builder(projector.getViewName())
                .eventTypes(
                    type(WalletStatementOpened.class),
                    type(WalletStatementClosed.class),
                    type(DepositMade.class),
                    type(WithdrawalMade.class),
                    type(MoneyTransferred.class)
                )
                .anyOfTags(WALLET_ANY_OF_TAGS)
                .build();
    }
}
