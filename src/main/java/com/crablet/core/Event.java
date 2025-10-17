package com.crablet.core;

import java.time.Instant;
import java.util.List;

/**
 * Event represents a single event in the event store.
 * This is a pure data record with no business logic.
 */
public record Event(
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
