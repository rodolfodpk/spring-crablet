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
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tagKey + "=%')");
        }

        Set<String> anyOfTags = selection.getAnyOfTags();
        if (!anyOfTags.isEmpty()) {
            String anyOfCondition = anyOfTags.stream()
                    .map(tagKey -> "t LIKE '" + tagKey + "=%'")
                    .collect(Collectors.joining(" OR "));
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");
        }

        for (Map.Entry<String, String> entry : selection.getExactTags().entrySet()) {
            conditions.add("'" + entry.getKey() + "=" + entry.getValue() + "' = ANY(tags)");
        }

        return conditions.isEmpty() ? "TRUE" : String.join(" AND ", conditions);
    }
}
