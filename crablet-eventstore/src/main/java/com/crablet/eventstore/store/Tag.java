package com.crablet.eventstore.store;

import java.util.List;

/**
 * Tag represents a key-value pair for event metadata.
 * This is a pure data record with no business logic.
 * 
 * <p><strong>Storage Format:</strong>
 * Tags are stored in PostgreSQL as {@code TEXT[]} with format {@code "key=value"} (using equals sign).
 * When querying tags in SQL, use patterns like {@code LIKE 'key=%'} or exact matches like {@code 'key=value'}.
 * 
 * <p><strong>Example:</strong>
 * <pre>{@code
 * Tag tag = new Tag("wallet_id", "wallet-123");
 * // Stored in database as: "wallet_id=wallet-123"
 * // Query with: WHERE EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE 'wallet_id=%')
 * }</pre>
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
