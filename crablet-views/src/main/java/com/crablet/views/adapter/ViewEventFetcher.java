package com.crablet.views.adapter;

import com.crablet.eventpoller.AbstractJdbcEventFetcher;
import com.crablet.views.config.ViewSubscriptionConfig;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event fetcher for view projections.
 * Fetches events from read replica using event type and tag filtering based on subscription config.
 */
public class ViewEventFetcher extends AbstractJdbcEventFetcher<String> {

    private final Map<String, ViewSubscriptionConfig> subscriptions;

    public ViewEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            Map<String, ViewSubscriptionConfig> subscriptions) {
        super(readDataSource);
        this.subscriptions = subscriptions;
    }

    @Override
    protected String buildSqlFilter(String viewName) {
        ViewSubscriptionConfig subscription = subscriptions.get(viewName);
        if (subscription == null) {
            log.warn("Subscription not found for view: {} (available: {})", viewName, subscriptions.keySet());
            return null;
        }

        Set<String> eventTypes = subscription.getEventTypes();
        Set<String> requiredTags = subscription.getRequiredTags();
        Set<String> anyOfTags = subscription.getAnyOfTags();

        List<String> conditions = new ArrayList<>();

        if (!eventTypes.isEmpty()) {
            String typeCondition = eventTypes.stream()
                .map(type -> "'" + type + "'")
                .collect(Collectors.joining(", "));
            conditions.add("type IN (" + typeCondition + ")");
        }

        for (String tagKey : requiredTags) {
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tagKey + "=%')");
        }

        if (!anyOfTags.isEmpty()) {
            String anyOfCondition = anyOfTags.stream()
                .map(tagKey -> "t LIKE '" + tagKey + "=%'")
                .collect(Collectors.joining(" OR "));
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");
        }

        return conditions.isEmpty() ? "TRUE" : String.join(" AND ", conditions);
    }
}
