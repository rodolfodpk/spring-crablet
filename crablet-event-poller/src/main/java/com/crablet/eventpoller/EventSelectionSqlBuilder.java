package com.crablet.eventpoller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds SQL WHERE fragments for {@link EventSelection}.
 */
public final class EventSelectionSqlBuilder {

    private EventSelectionSqlBuilder() {}

    /**
     * Builds a SQL fragment that can be placed inside a {@code WHERE (...)} clause.
     * Returns {@code "TRUE"} when the selection imposes no filtering.
     */
    public static String buildWhereClause(EventSelection selection) {
        List<String> conditions = new ArrayList<>();

        Set<String> eventTypes = selection.getEventTypes();
        if (!eventTypes.isEmpty()) {
            String typeList = eventTypes.stream()
                    .map(type -> "'" + type + "'")
                    .collect(Collectors.joining(", "));
            conditions.add("type IN (" + typeList + ")");
        }

        for (String tagKey : selection.getRequiredTags()) {
            conditions.add("EXISTS (SELECT 1 FROM crablet_event_tags t WHERE t.position = crablet_events.position AND t.key = '" + tagKey + "')");
        }

        Set<String> anyOfTags = selection.getAnyOfTags();
        if (!anyOfTags.isEmpty()) {
            String keyList = anyOfTags.stream()
                    .map(k -> "'" + k + "'")
                    .collect(Collectors.joining(", "));
            conditions.add("EXISTS (SELECT 1 FROM crablet_event_tags t WHERE t.position = crablet_events.position AND t.key IN (" + keyList + "))");
        }

        for (Map.Entry<String, String> entry : selection.getExactTags().entrySet()) {
            conditions.add("EXISTS (SELECT 1 FROM crablet_event_tags t WHERE t.position = crablet_events.position AND t.key = '" + entry.getKey() + "' AND t.value = '" + entry.getValue() + "')");
        }

        return conditions.isEmpty() ? "TRUE" : String.join(" AND ", conditions);
    }
}
