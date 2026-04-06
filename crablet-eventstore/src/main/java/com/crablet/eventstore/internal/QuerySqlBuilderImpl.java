package com.crablet.eventstore.internal;

import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Public implementation of QuerySqlBuilder.
 * Directly instantiated by EventStoreImpl and EventRepositoryImpl.
 */
class QuerySqlBuilderImpl implements QuerySqlBuilder {

    @Override
    public String buildWhereClause(Query query, @Nullable StreamPosition after, List<Object> params) {
        StringBuilder whereClause = new StringBuilder();

        // after_position parameter
        long afterPosition = after != null ? after.position() : 0L;
        if (afterPosition > 0) {
            whereClause.append("position > ?");
            params.add(afterPosition);
        }

        // Build OR conditions for each QueryItem
        if (!query.isEmpty()) {
            List<String> orConditions = new ArrayList<>();

            for (QueryItem item : query.items()) {
                StringBuilder condition = new StringBuilder("(");

                // Event types filter
                if (!item.eventTypes().isEmpty()) {
                    condition.append("type = ANY(?)");
                    params.add(item.eventTypes().toArray(new String[0]));
                }

                // Tags filter
                if (!item.tags().isEmpty()) {
                    if (!item.eventTypes().isEmpty()) {
                        condition.append(" AND ");
                    }

                    String[] tagStrings = item.tags().stream()
                            .map(tag -> tag.key() + "=" + tag.value())
                            .toArray(String[]::new);
                    condition.append("tags @> ?::text[]");
                    params.add(tagStrings);
                }

                condition.append(")");

                if (condition.length() > 2) { // More than just "()"
                    orConditions.add(condition.toString());
                }
            }

            if (!orConditions.isEmpty()) {
                if (afterPosition > 0) {
                    whereClause.append(" AND ");
                }
                whereClause.append("(").append(String.join(" OR ", orConditions)).append(")");
            }
        }

        return whereClause.toString();
    }
}
