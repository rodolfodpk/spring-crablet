package com.crablet.eventstore;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating AppendCondition with dual conditions.
 * <p>
 * DCB Pattern: Separates concurrency check (with stream position) and idempotency check (no stream position).
 */
public class AppendConditionBuilder {
    private final @Nullable Query decisionModelQuery;
    private final @Nullable StreamPosition streamPosition;
    private final List<QueryItem> idempotencyItems = new ArrayList<>();

    /**
     * Creates a builder with a decision model query and stream position.
     * Prefer the {@link #of(Query, StreamPosition)} static factory over this constructor.
     */
    public AppendConditionBuilder(@Nullable Query decisionModelQuery, @Nullable StreamPosition streamPosition) {
        this.decisionModelQuery = decisionModelQuery;
        this.streamPosition = streamPosition;
    }

    /**
     * Static factory — preferred over {@code new AppendConditionBuilder(query, streamPosition)}.
     */
    public static AppendConditionBuilder of(@Nullable Query decisionModelQuery, @Nullable StreamPosition streamPosition) {
        return new AppendConditionBuilder(decisionModelQuery, streamPosition);
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
     * - concurrencyQuery: decision model query + stream position (checks events AFTER last read)
     * - idempotencyQuery: operation ID tags (checks ALL events, no stream position limit); Query.empty() = no check
     */
    public AppendCondition build() {
        var idempotencyQuery = idempotencyItems.isEmpty()
            ? Query.empty()
            : Query.of(idempotencyItems);

        return AppendCondition.of(streamPosition, decisionModelQuery, idempotencyQuery);
    }
}
