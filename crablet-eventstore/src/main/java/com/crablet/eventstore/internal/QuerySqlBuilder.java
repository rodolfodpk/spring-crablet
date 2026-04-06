package com.crablet.eventstore.internal;

import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.query.Query;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Builds SQL WHERE clauses from Query objects.
 * Separates SQL generation from query execution for better abstraction.
 */
public interface QuerySqlBuilder {
    /**
     * Build a WHERE clause from a Query and Cursor.
     *
     * @param query The query to filter events
     * @param after StreamPosition to query events after (null for all events)
     * @param params List to collect query parameters (output parameter)
     * @return WHERE clause SQL string (without "WHERE" keyword), empty if no conditions
     */
    String buildWhereClause(Query query, @Nullable StreamPosition after, List<Object> params);
}
