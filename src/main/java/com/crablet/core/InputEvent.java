package com.crablet.core;

import java.util.List;

/**
 * InputEvent represents an event to be appended to the store.
 * This is a pure data record with no business logic.
 */
public record InputEvent(
    String type,
    List<Tag> tags,
    byte[] data
) {
    /**
     * Create an input event from type, tags, and data.
     */
    public static InputEvent of(String type, List<Tag> tags, byte[] data) {
        return new InputEvent(type, tags, data);
    }
    
    /**
     * Create an input event from type, tags, and JSON string.
     */
    public static InputEvent of(String type, List<Tag> tags, String jsonData) {
        return new InputEvent(type, tags, jsonData.getBytes());
    }
    
    /**
     * Create an input event from type and JSON string (no tags).
     */
    public static InputEvent of(String type, String jsonData) {
        return new InputEvent(type, List.of(), jsonData.getBytes());
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
