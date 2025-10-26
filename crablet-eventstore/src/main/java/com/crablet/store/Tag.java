package com.crablet.store;

import java.util.List;

/**
 * Tag represents a key-value pair for event metadata.
 * This is a pure data record with no business logic.
 */
public record Tag(String key, String value) {
    /**
     * Create a tag from a key-value pair.
     */
    public static Tag of(String key, String value) {
        return new Tag(key, value);
    }

    /**
     * Create tags from alternating key-value pairs.
     */
    public static List<Tag> of(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even");
        }

        var tags = new java.util.ArrayList<Tag>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            tags.add(new Tag(keyValuePairs[i], keyValuePairs[i + 1]));
        }
        return tags;
    }

    /**
     * Create a tag from state name and ID.
     * Uses the storage format as the value.
     */
    public static Tag stateIdentifier(String stateName, String stateId) {
        return new Tag("state_identifier", stateId + "@" + stateName);
    }

    /**
     * Create tags from state name and ID pairs.
     */
    public static List<Tag> stateIdentifiers(String stateName, String stateId, String stateName2, String stateId2) {
        return List.of(stateIdentifier(stateName, stateId), stateIdentifier(stateName2, stateId2));
    }

    /**
     * Create a tag for an event type.
     */
    public static Tag eventType(String eventName) {
        return new Tag("event_type", eventName);
    }

    /**
     * Create a single tag as a List for convenience.
     * Useful when only one tag is needed instead of List.of(new Tag(...))
     */
    public static List<Tag> single(String key, String value) {
        return List.of(new Tag(key, value));
    }

}
