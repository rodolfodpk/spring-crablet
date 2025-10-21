package com.crablet.core;

import java.util.ArrayList;
import java.util.List;

/**
 * AppendEvent represents an event to be appended to the store.
 * This is a pure data record with no business logic.
 * <p>
 * Use AppendEvent for events being written to the store via append().
 * Use StoredEvent for events queried from the store via query().
 */
public record AppendEvent(
        String type,
        List<Tag> tags,
        byte[] data
) {

    /**
     * Fluent builder for creating AppendEvent with multiple tags.
     */
    public static class Builder {
        private String type;
        private final List<Tag> tags = new ArrayList<>();
        private byte[] data;
        
        private Builder(String type) {
            this.type = type;
        }
        
        /**
         * Add a tag to this event.
         */
        public Builder tag(String key, String value) {
            tags.add(new Tag(key, value));
            return this;
        }
        
        /**
         * Set the event data from JSON string.
         */
        public Builder data(String jsonData) {
            this.data = jsonData.getBytes();
            return this;
        }
        
        /**
         * Set the event data from byte array.
         */
        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }
        
        /**
         * Build the AppendEvent.
         */
        public AppendEvent build() {
            return new AppendEvent(type, List.copyOf(tags), data);
        }
    }

    /**
     * Start building an AppendEvent with fluent API.
     */
    public static Builder builder(String type) {
        return new Builder(type);
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
