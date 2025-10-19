package com.crablet.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating AppendCondition with idempotency checks.
 * <p>
 * DCB Pattern: Combines decision model query + idempotency check.
 */
public class AppendConditionBuilder {
    private final Query decisionModelQuery;
    private final Cursor cursor;
    private final List<QueryItem> additionalItems = new ArrayList<>();

    AppendConditionBuilder(Query decisionModelQuery, Cursor cursor) {
        this.decisionModelQuery = decisionModelQuery;
        this.cursor = cursor;
    }

    /**
     * Add idempotency check for duplicate operations.
     *
     * @param eventType The event type to check for duplicates
     * @param tags      Tags identifying the unique operation (e.g., transfer_id)
     */
    public AppendConditionBuilder withIdempotencyCheck(String eventType, Tag... tags) {
        additionalItems.add(QueryItem.of(List.of(eventType), List.of(tags)));
        return this;
    }

    /**
     * Build the final AppendCondition.
     * Combines decision model query + idempotency checks.
     */
    public AppendCondition build() {
        // Combine decision model query with additional idempotency checks
        List<QueryItem> allItems = new ArrayList<>(decisionModelQuery.items());
        allItems.addAll(additionalItems);

        Query fullQuery = Query.of(allItems);
        return AppendCondition.of(cursor, fullQuery);
    }
}

