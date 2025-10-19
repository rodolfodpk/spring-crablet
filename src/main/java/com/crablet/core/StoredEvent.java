package com.crablet.core;

import java.time.Instant;
import java.util.List;

/**
 * StoredEvent represents an event retrieved from the event store.
 * Includes database-specific fields like transaction ID, position, and timestamp.
 * <p>
 * Use AppendEvent for events being appended to the store.
 * Use StoredEvent for events queried from the store.
 */
public record StoredEvent(
        String type,
        List<Tag> tags,
        byte[] data,
        String transactionId,
        long position,
        Instant occurredAt
) {
    /**
     * Check if this event has a specific tag.
     */
    public boolean hasTag(String key, String value) {
        return tags.contains(new Tag(key, value));
    }

    /**
     * Get the value of a specific tag.
     */
    public java.util.Optional<String> getTagValue(String key) {
        return tags.stream()
                .filter(tag -> tag.key().equals(key))
                .map(Tag::value)
                .findFirst();
    }

    /**
     * Check if this event has any of the specified tags.
     */
    public boolean hasAnyTag(List<Tag> targetTags) {
        return targetTags.stream().anyMatch(tags::contains);
    }
}
