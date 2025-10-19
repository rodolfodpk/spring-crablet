package com.crablet.core;

import java.util.List;

/**
 * QueryItem represents a single item in a query.
 * This is a pure data record with no business logic.
 */
public record QueryItem(
        List<String> eventTypes,
        List<Tag> tags
) {
    /**
     * Create a query item from event types and tags.
     */
    public static QueryItem of(List<String> eventTypes, List<Tag> tags) {
        return new QueryItem(eventTypes, tags);
    }

    /**
     * Create a query item from event types only.
     */
    public static QueryItem ofTypes(List<String> eventTypes) {
        return new QueryItem(eventTypes, List.of());
    }

    /**
     * Create a query item from tags only.
     */
    public static QueryItem ofTags(List<Tag> tags) {
        return new QueryItem(List.of(), tags);
    }

    /**
     * Create a query item from a single event type.
     */
    public static QueryItem ofType(String eventType) {
        return new QueryItem(List.of(eventType), List.of());
    }

    /**
     * Create a query item from a single tag.
     */
    public static QueryItem ofTag(Tag tag) {
        return new QueryItem(List.of(), List.of(tag));
    }

    /**
     * Check if this query item matches any event types.
     */
    public boolean hasEventTypes() {
        return !eventTypes.isEmpty();
    }

    /**
     * Check if this query item matches any tags.
     */
    public boolean hasTags() {
        return !tags.isEmpty();
    }
}
