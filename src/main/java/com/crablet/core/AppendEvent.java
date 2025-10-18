package com.crablet.core;

import java.util.List;

/**
 * AppendEvent represents an event to be appended to the store.
 * This is a pure data record with no business logic.
 * 
 * Use AppendEvent for events being written to the store via append().
 * Use StoredEvent for events queried from the store via query().
 */
public record AppendEvent(
    String type,
    List<Tag> tags,
    byte[] data
) {
    /**
     * Create an append event from type, tags, and data.
     */
    public static AppendEvent of(String type, List<Tag> tags, byte[] data) {
        return new AppendEvent(type, tags, data);
    }
    
    /**
     * Create an append event from type, tags, and JSON string.
     */
    public static AppendEvent of(String type, List<Tag> tags, String jsonData) {
        return new AppendEvent(type, tags, jsonData.getBytes());
    }
    
    /**
     * Create an append event from type and JSON string (no tags).
     */
    public static AppendEvent of(String type, String jsonData) {
        return new AppendEvent(type, List.of(), jsonData.getBytes());
    }
    
    
    /**
     * Check if this input event has a specific tag.
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
}
