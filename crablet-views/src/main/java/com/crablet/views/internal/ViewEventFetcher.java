package com.crablet.views.internal;

import com.crablet.eventpoller.AbstractJdbcEventFetcher;
import com.crablet.eventpoller.EventSelectionSqlBuilder;
import com.crablet.views.ViewSubscription;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Event fetcher for view projections.
 * Fetches events from read replica using event type and tag filtering based on subscription config.
 */
public class ViewEventFetcher extends AbstractJdbcEventFetcher<String> {

    private final Map<String, ViewSubscription> subscriptions;

    public ViewEventFetcher(
            DataSource readDataSource,
            Map<String, ViewSubscription> subscriptions) {
        super(readDataSource);
        this.subscriptions = subscriptions;
    }

    @Override
    protected String buildSqlFilter(String viewName) {
        ViewSubscription subscription = subscriptions.get(viewName);
        if (subscription == null) {
            log.warn("Subscription not found for view: {} (available: {})", viewName, subscriptions.keySet());
            return null;
        }
        return EventSelectionSqlBuilder.buildWhereClause(subscription);
    }
}
