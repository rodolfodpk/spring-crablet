package com.crablet.eventstore.store;

import org.jspecify.annotations.Nullable;

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
        private @Nullable Object eventData;
        
        private Builder(String type) {
            this.type = type;
        }
        
        /**
         * Add all tags from a list to this event. Useful for applying period tags
         * produced by {@link com.crablet.eventstore.period.PeriodTags}.
         */
        public Builder tags(List<Tag> moreTags) {
            tags.addAll(moreTags);
            return this;
        }

        /**
         * Add a tag to this event.
         */
        public Builder tag(String key, String value) {
            tags.add(new Tag(key, value));
            return this;
        }

        /**
         * Add an int tag to this event. Converts the value to its string representation.
         */
        public Builder tag(String key, int value) {
            return tag(key, String.valueOf(value));
        }

        /**
         * Add a nullable Integer tag to this event. Skips the tag silently when value is null,
         * which is useful for optional period components (e.g., day, hour).
         */
        public Builder tag(String key, @Nullable Integer value) {
            if (value != null) {
                tag(key, String.valueOf(value));
            }
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
