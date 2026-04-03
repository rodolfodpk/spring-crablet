package com.crablet.eventstore.dcb;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.Tag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating AppendCondition with dual conditions.
 * <p>
 * DCB Pattern: Separates concurrency check (with cursor) and idempotency check (no cursor).
 */
public class AppendConditionBuilder {
    private final @Nullable Query decisionModelQuery;
    private final @Nullable Cursor cursor;
    private final List<QueryItem> idempotencyItems = new ArrayList<>();

    /**
     * Creates a builder with a decision model query and cursor position.
     * Prefer the {@link #of(Query, Cursor)} static factory over this constructor.
     */
    public AppendConditionBuilder(@Nullable Query decisionModelQuery, @Nullable Cursor cursor) {
        this.decisionModelQuery = decisionModelQuery;
        this.cursor = cursor;
    }

    /**
     * Static factory — preferred over {@code new AppendConditionBuilder(query, cursor)}.
     */
    public static AppendConditionBuilder of(@Nullable Query decisionModelQuery, @Nullable Cursor cursor) {
        return new AppendConditionBuilder(decisionModelQuery, cursor);
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
    public AppendConditionBuilder withIdempotencyCheck(@Nullable String eventType, @Nullable String tagKey, @Nullable String tagValue) {
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
        @Nullable Query alreadyExistsQuery = idempotencyItems.isEmpty()
            ? null
            : Query.of(idempotencyItems);

        return AppendCondition.of(cursor, stateChangedQuery, alreadyExistsQuery);
    }
}

