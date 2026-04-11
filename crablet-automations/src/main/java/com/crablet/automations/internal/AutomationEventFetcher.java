package com.crablet.automations.internal;

import com.crablet.automations.AutomationDefinition;
import com.crablet.automations.AutomationHandler;
import com.crablet.eventpoller.AbstractJdbcEventFetcher;
import com.crablet.eventpoller.EventSelectionSqlBuilder;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fetches events for automation processors using event type and tag filtering.
 */
public class AutomationEventFetcher extends AbstractJdbcEventFetcher<String> {

    private final Map<String, AutomationHandler> handlers;

    public AutomationEventFetcher(DataSource readDataSource, Map<String, AutomationHandler> handlers) {
        super(readDataSource);
        this.handlers = handlers;
    }

    @Override
    protected String buildSqlFilter(String automationName) {
        AutomationDefinition definition = resolveDefinition(automationName);
        if (definition == null) {
            return null;
        }
        return EventSelectionSqlBuilder.buildWhereClause(definition);
    }

    private AutomationDefinition resolveDefinition(String automationName) {
        AutomationHandler handler = handlers.get(automationName);
        if (handler != null) {
            return handler;
        }

        Set<String> available = new HashSet<>(handlers.keySet());
        log.warn("No handler found for automation: {} (available: {})", automationName, available);
        return null;
    }
}
