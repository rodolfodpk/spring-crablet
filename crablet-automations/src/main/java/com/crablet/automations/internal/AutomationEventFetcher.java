package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.AutomationSubscription;
import com.crablet.eventpoller.AbstractJdbcEventFetcher;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fetches events for automation processors using event type and tag filtering.
 * Supports both webhook subscriptions and in-process handlers.
 */
public class AutomationEventFetcher extends AbstractJdbcEventFetcher<String> {

    private final Map<String, AutomationSubscription> subscriptions;
    private final Map<String, AutomationHandler> inProcessHandlers;

    public AutomationEventFetcher(DataSource readDataSource,
                                   Map<String, AutomationSubscription> subscriptions,
                                   Map<String, AutomationHandler> inProcessHandlers) {
        super(readDataSource);
        this.subscriptions = subscriptions;
        this.inProcessHandlers = inProcessHandlers;
    }

    @Override
    protected String buildSqlFilter(String automationName) {
        Set<String> eventTypes;
        Set<String> requiredTags;
        Set<String> anyOfTags;

        AutomationSubscription subscription = subscriptions.get(automationName);
        if (subscription != null) {
            eventTypes = subscription.getEventTypes();
            requiredTags = subscription.getRequiredTags();
            anyOfTags = subscription.getAnyOfTags();
        } else {
            AutomationHandler handler = inProcessHandlers.get(automationName);
            if (handler == null) {
                Set<String> available = new HashSet<>();
                available.addAll(subscriptions.keySet());
                available.addAll(inProcessHandlers.keySet());
                log.warn("Neither subscription nor handler found for automation: {} (available: {})",
                        automationName, available);
                return null;
            }
            eventTypes = handler.getEventTypes();
            requiredTags = handler.getRequiredTags();
            anyOfTags = handler.getAnyOfTags();
        }

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
