package com.crablet.eventstore.query;

import com.crablet.eventstore.AppendConditionBuilder;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for constructing DCB queries.
 * Business-agnostic, reusable across domains.
 */
public class QueryBuilder {
    private final List<QueryItem> items = new ArrayList<>();

    private QueryBuilder() {
    }

    /**
     * Entry point for the fluent query DSL.
     * Chain {@link #event}, {@link #events}, or {@link #matching} calls, then {@link #build}.
     */
    public static QueryBuilder builder() {
        return new QueryBuilder();
    }

    /**
     * Helper method for creating Tag objects.
     */
    public static Tag tag(String name, String value) {
        return new Tag(name, value);
    }

    /**
     * Add a query item matching specific event types and tags.
     */
    public QueryBuilder matching(String[] eventTypes, Tag... tags) {
        items.add(QueryItem.of(Arrays.asList(eventTypes), Arrays.asList(tags)));
        return this;
    }

    /**
     * Add multiple query items at once.
     */
    public QueryBuilder matching(List<QueryItem> queryItems) {
        items.addAll(queryItems);
        return this;
    }

    /**
     * Build the final Query.
     */
    public Query build() {
        return Query.of(new ArrayList<>(items));
    }

    /**
     * Convert this query into an {@link AppendConditionBuilder} for advanced
     * direct-{@link com.crablet.eventstore.EventStore} usage.
     * <p>
     * Most applications should use the semantic append methods on
     * {@code EventStore} directly instead of constructing low-level append
     * conditions.
     */
    public AppendConditionBuilder toAppendCondition(StreamPosition streamPosition) {
        return new AppendConditionBuilder(this.build(), streamPosition);
    }

    /**
     * Single event with single tag (most common case).
     */
    public QueryBuilder event(String eventType, String tagName, String tagValue) {
        items.add(QueryItem.of(List.of(eventType), List.of(new Tag(tagName, tagValue))));
        return this;
    }

    /**
     * Multiple events with single tag (common pattern).
     * Returns EventsContext for chaining.
     */
    public EventsContext events(String... eventTypes) {
        return new EventsContext(this, List.of(eventTypes));
    }

    /**
     * Context for chaining events with tags.
     */
    public static class EventsContext {
        private final QueryBuilder builder;
        private final List<String> eventTypes;

        EventsContext(QueryBuilder builder, List<String> eventTypes) {
            this.builder = builder;
            this.eventTypes = eventTypes;
        }

        /**
         * Add single tag to the events.
         */
        public QueryBuilder tag(String name, String value) {
            builder.items.add(QueryItem.of(eventTypes, List.of(new Tag(name, value))));
            return builder;
        }

        /**
         * Add multiple tags to the events.
         */
        public QueryBuilder tags(Tag... tags) {
            builder.items.add(QueryItem.of(eventTypes, Arrays.asList(tags)));
            return builder;
        }
    }
}
