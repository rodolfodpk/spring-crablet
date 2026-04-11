package com.crablet.views;

import com.crablet.eventstore.StoredEvent;

import javax.sql.DataSource;
import java.util.List;

/**
 * Marker interface for view projectors.
 * Projectors implementing this interface declare which view they handle and
 * process batches using the primary/write datasource.
 */
public interface ViewProjector {

    /**
     * Get the view name this projector handles.
     */
    String getViewName();

    /**
     * Handle a batch of events using the primary/write datasource.
     */
    int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) throws Exception;

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
