package com.crablet.views;

import com.crablet.eventstore.StoredEvent;

import java.util.List;

/**
 * Marker interface for view projectors.
 * Projectors implementing this interface declare which view they handle and
 * process batches using the primary/write datasource.
 * <p>
 * <strong>DataSource ownership:</strong> the write datasource
 * is injected into the projector's constructor, not passed per call. This keeps the
 * public interface free of infrastructure concerns and aligns with the rest of the framework.
 * Implementations must inject the framework's write datasource, never the read
 * replica, because view projection writes must go to the primary database.
 */
public interface ViewProjector {

    /**
     * Get the view name this projector handles.
     */
    String getViewName();

    /**
     * Handle a batch of events.
     * The write datasource is owned by the projector (injected at construction time).
     */
    int handle(String viewName, List<StoredEvent> events) throws Exception;

    /**
     * Build a {@link ViewSubscription} for this projector using the given event types.
     * <p>
     * Eliminates the need to repeat the view name in a separate {@code @Bean} method:
     * <pre>{@code
     * @Bean
     * public ViewSubscription walletBalanceViewSubscription(WalletBalanceViewProjector projector) {
     *     return projector.subscription(type(WalletOpened.class), type(DepositMade.class));
     * }
     * }</pre>
     * For subscriptions that also require tag filtering, use
     * {@link ViewSubscription#builder(String)} directly.
     */
    default ViewSubscription subscription(String... eventTypes) {
        return ViewSubscription.builder(getViewName())
                .eventTypes(eventTypes)
                .build();
    }
}
