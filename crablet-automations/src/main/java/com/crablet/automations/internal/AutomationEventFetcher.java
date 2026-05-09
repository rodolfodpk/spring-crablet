package com.crablet.automations.internal;

import com.crablet.automations.AutomationDefinition;
import com.crablet.eventpoller.AbstractJdbcEventFetcher;
import com.crablet.eventpoller.EventSelectionSqlBuilder;

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fetches events for automation processors using event type and tag filtering.
 */
public class AutomationEventFetcher extends AbstractJdbcEventFetcher<String> {

    private final Map<String, AutomationDefinition> definitions;

    public AutomationEventFetcher(DataSource readDataSource, Map<String, AutomationDefinition> definitions) {
        super(readDataSource);
        this.definitions = definitions;
    }

    @Override
    protected @Nullable String buildSqlFilter(String automationName) {
        AutomationDefinition definition = definitions.get(automationName);
        if (definition != null) {
            return EventSelectionSqlBuilder.buildWhereClause(definition);
        }

        Set<String> available = new HashSet<>(definitions.keySet());
        log.warn("No definition found for automation: {} (available: {})", automationName, available);
        return null;
    }
}
