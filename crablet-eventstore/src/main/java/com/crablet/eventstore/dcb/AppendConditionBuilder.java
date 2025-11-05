package com.crablet.eventstore.dcb;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.Tag;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating AppendCondition with dual conditions.
 * <p>
 * DCB Pattern: Separates concurrency check (with cursor) and idempotency check (no cursor).
 */
public class AppendConditionBuilder {
    private final Query decisionModelQuery;
    private final Cursor cursor;
    private final List<QueryItem> idempotencyItems = new ArrayList<>();

    public AppendConditionBuilder(Query decisionModelQuery, Cursor cursor) {
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
        idempotencyItems.add(QueryItem.of(List.of(eventType), List.of(tags)));
        return this;
    }

    /**
     * Add idempotency check for duplicate operations (convenience method).
     * 
     * @param eventType The event type to check for duplicates
     * @param tagKey    The tag key identifying the unique operation
     * @param tagValue  The tag value identifying the unique operation
     */
    public AppendConditionBuilder withIdempotencyCheck(String eventType, String tagKey, String tagValue) {
        idempotencyItems.add(QueryItem.of(List.of(eventType), List.of(new Tag(tagKey, tagValue))));
        return this;
    }

    /**
     * Build the final AppendCondition with dual conditions.
     * - stateChanged: decision model query + cursor (checks events AFTER last read)
     * - alreadyExists: operation ID tags (checks ALL events, no cursor limit)
     */
    public AppendCondition build() {
        // Concurrency check: decision model query (with cursor)
        Query stateChangedQuery = decisionModelQuery;
        
        // Idempotency check: separate query (no cursor limit)
        Query alreadyExistsQuery = idempotencyItems.isEmpty() 
            ? null 
            : Query.of(idempotencyItems);
        
        return AppendCondition.of(cursor, stateChangedQuery, alreadyExistsQuery);
    }
}

