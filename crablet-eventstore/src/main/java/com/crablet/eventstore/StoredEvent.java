package com.crablet.eventstore;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * StoredEvent represents an event retrieved from the event store.
 * Includes database-specific fields like transaction ID, position, and timestamp.
 * <p>
 * Use AppendEvent for events being appended to the store.
 * Use StoredEvent for events queried from the store.
 * <p>
 * {@code correlationId} ties together all events that originated from the same
 * business operation (propagated from HTTP {@code X-Correlation-ID} header through
 * automation chains). {@code causationId} is the {@code position} of the event that
 * directly caused this one; {@code null} for events triggered by a direct user action.
 */
public record StoredEvent(
        String type,
        List<Tag> tags,
        byte[] data,
        String transactionId,
        long position,
        Instant occurredAt,
        @Nullable UUID correlationId,
        @Nullable Long causationId
) {
    /**
     * Convenience constructor for code that does not supply correlation/causation context
     * (tests, legacy construction sites). Both fields default to {@code null}.
     */
    public StoredEvent(String type, List<Tag> tags, byte[] data,
                       String transactionId, long position, Instant occurredAt) {
        this(type, tags, data, transactionId, position, occurredAt, null, null);
    }
    /**
     * Check if this event has a specific tag.
     */
    public boolean hasTag(String key, String value) {
        return tags.contains(new Tag(key, value));
    }

    /**
     * Check if this event has any of the specified tags.
     */
    public boolean hasAnyTag(List<Tag> targetTags) {
        return targetTags.stream().anyMatch(tags::contains);
    }
}
