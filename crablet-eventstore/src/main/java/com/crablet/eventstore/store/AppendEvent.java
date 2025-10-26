package com.crablet.eventstore.store;

import java.util.ArrayList;
import java.util.List;

/**
 * AppendEvent represents an event to be appended to the store.
 * This is a pure data record with no business logic.
 * <p>
 * Use AppendEvent for events being written to the store via append().
 * Use StoredEvent for events queried from the store via query().
 * <p>
 * Event data is passed as an Object and will be serialized by EventStore implementation.
 */
public record AppendEvent(
        String type,
        List<Tag> tags,
        Object eventData
) {

    /**
     * Fluent builder for creating AppendEvent with multiple tags.
     */
    public static class Builder {
        private final String type;
        private final List<Tag> tags = new ArrayList<>();
        private Object eventData;
        
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
         * Set the event data as an object.
         * The object will be serialized by EventStore implementation.
         */
        public Builder data(Object eventData) {
            this.eventData = eventData;
            return this;
        }
        
        /**
         * Build the AppendEvent.
         */
        public AppendEvent build() {
            if (eventData == null) {
                throw new IllegalArgumentException("Event data cannot be null");
            }
            return new AppendEvent(type, List.copyOf(tags), eventData);
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
}
