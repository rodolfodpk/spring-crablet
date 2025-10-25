package com.crablet.eventstore;

import java.util.List;

/**
 * Query represents a query for events in the store.
 * This is a pure data record with no business logic.
 */
public record Query(List<QueryItem> items) {
    /**
     * Create a query from query items.
     */
    public static Query of(List<QueryItem> items) {
        return new Query(items);
    }

    /**
     * Create a query from a single query item.
     */
    public static Query of(QueryItem item) {
        return new Query(List.of(item));
    }

    /**
     * Create an empty query (matches all events).
     */
    public static Query empty() {
        return new Query(List.of());
    }

    /**
     * Create a query for a single event type with a single tag.
     * This is the most common DCB query pattern.
     */
    public static Query forEventAndTag(String eventType, String tagKey, String tagValue) {
        return Query.of(QueryItem.of(
                List.of(eventType),
                List.of(new Tag(tagKey, tagValue))
        ));
    }

    /**
     * Create a query for a single event type with multiple tags.
     */
    public static Query forEventAndTags(String eventType, List<Tag> tags) {
        return Query.of(QueryItem.of(List.of(eventType), tags));
    }

    /**
     * Create a query for a single event type only.
     */
    public static Query forEvent(String eventType) {
        return Query.of(QueryItem.ofType(eventType));
    }

    /**
     * Create a query for multiple event types with tags.
     */
    public static Query forEventsAndTags(List<String> eventTypes, List<Tag> tags) {
        return Query.of(QueryItem.of(eventTypes, tags));
    }

    /**
     * Check if this query is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Get the number of query items.
     */
    public int size() {
        return items.size();
    }
}
