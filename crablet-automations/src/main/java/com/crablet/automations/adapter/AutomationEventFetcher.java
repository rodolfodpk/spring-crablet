package com.crablet.automations.adapter;

import com.crablet.automations.AutomationSubscription;
import com.crablet.eventpoller.AbstractJdbcEventFetcher;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fetches events for automation processors using event type and tag filtering.
 */
public class AutomationEventFetcher extends AbstractJdbcEventFetcher<String> {

    private final Map<String, AutomationSubscription> subscriptions;

    public AutomationEventFetcher(DataSource readDataSource, Map<String, AutomationSubscription> subscriptions) {
        super(readDataSource);
        this.subscriptions = subscriptions;
    }

    @Override
    protected String buildSqlFilter(String automationName) {
        AutomationSubscription subscription = subscriptions.get(automationName);
        if (subscription == null) {
            log.warn("Subscription not found for automation: {} (available: {})", automationName, subscriptions.keySet());
            return null;
        }

        Set<String> eventTypes = subscription.getEventTypes();
        Set<String> requiredTags = subscription.getRequiredTags();
        Set<String> anyOfTags = subscription.getAnyOfTags();

        List<String> conditions = new ArrayList<>();

        if (!eventTypes.isEmpty()) {
            String typeList = eventTypes.stream()
                .map(t -> "'" + t + "'")
                .collect(Collectors.joining(", "));
            conditions.add("type IN (" + typeList + ")");
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
