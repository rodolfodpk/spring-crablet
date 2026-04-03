package com.crablet.views;

import com.crablet.eventpoller.EventHandler;
import com.crablet.views.config.ViewSubscriptionConfig;

/**
 * Marker interface for view projectors.
 * Projectors implementing this interface declare which view they handle.
 */
public interface ViewProjector extends EventHandler<String> {

    /**
     * Get the view name this projector handles.
     */
    String getViewName();

    /**
     * Build a {@link ViewSubscriptionConfig} for this projector using the given event types.
     * <p>
     * Eliminates the need to repeat the view name in a separate {@code @Bean} method:
     * <pre>{@code
     * @Bean
     * public ViewSubscriptionConfig walletBalanceViewSubscription(WalletBalanceViewProjector projector) {
     *     return projector.subscription(type(WalletOpened.class), type(DepositMade.class));
     * }
     * }</pre>
     * For subscriptions that also require tag filtering, use
     * {@link ViewSubscriptionConfig#builder(String)} directly.
     */
    default ViewSubscriptionConfig subscription(String... eventTypes) {
        return ViewSubscriptionConfig.builder(getViewName())
                .eventTypes(eventTypes)
                .build();
    }
}

