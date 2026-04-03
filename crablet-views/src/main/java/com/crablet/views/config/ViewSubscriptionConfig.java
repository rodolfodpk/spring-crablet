package com.crablet.views.config;

import java.util.Set;

/**
 * Configuration for a view subscription.
 * Defines which events a view subscribes to by event type and/or tags.
 */
public class ViewSubscriptionConfig {
    
    private final String viewName;
    private final Set<String> eventTypes;
    private final Set<String> requiredTags;
    private final Set<String> anyOfTags;
    
    protected ViewSubscriptionConfig(
            String viewName,
            Set<String> eventTypes,
            Set<String> requiredTags,
            Set<String> anyOfTags) {
        this.viewName = viewName;
        this.eventTypes = eventTypes != null ? Set.copyOf(eventTypes) : Set.of();
        this.requiredTags = requiredTags != null ? Set.copyOf(requiredTags) : Set.of();
        this.anyOfTags = anyOfTags != null ? Set.copyOf(anyOfTags) : Set.of();
    }
    
    public String getViewName() {
        return viewName;
    }
    
    public Set<String> getEventTypes() {
        return eventTypes;
    }
    
    public Set<String> getRequiredTags() {
        return requiredTags;
    }
    
    public Set<String> getAnyOfTags() {
        return anyOfTags;
    }
    
    /** Entry point for building a subscription. Use {@code projector.subscription(eventTypes)} for the common case. */
    public static Builder builder(String viewName) {
        return new Builder(viewName);
    }

    public static class Builder {
        private final String viewName;
        private Set<String> eventTypes = Set.of();
        private Set<String> requiredTags = Set.of();
        private Set<String> anyOfTags = Set.of();

        public Builder(String viewName) {
            this.viewName = viewName;
        }

        /** Filter by event types (Set overload). */
        public Builder eventTypes(Set<String> eventTypes) {
            this.eventTypes = eventTypes;
            return this;
        }

        /** Filter by event types (varargs overload). Use {@code type(MyEvent.class)} for type safety. */
        public Builder eventTypes(String... eventTypes) {
            this.eventTypes = Set.of(eventTypes);
            return this;
        }

        /** ALL of these tag keys must be present on the event for it to be delivered. */
        public Builder requiredTags(Set<String> requiredTags) {
            this.requiredTags = requiredTags;
            return this;
        }

        /** ALL of these tag keys must be present on the event for it to be delivered (varargs overload). */
        public Builder requiredTags(String... requiredTags) {
            this.requiredTags = Set.of(requiredTags);
            return this;
        }

        /** At least ONE of these tag keys must be present on the event for it to be delivered. */
        public Builder anyOfTags(Set<String> anyOfTags) {
            this.anyOfTags = anyOfTags;
            return this;
        }

        /** At least ONE of these tag keys must be present on the event for it to be delivered (varargs overload). */
        public Builder anyOfTags(String... anyOfTags) {
            this.anyOfTags = Set.of(anyOfTags);
            return this;
        }

        /**
         * Convenience method: require a single tag.
         */
        public Builder requireTag(String tagKey) {
            this.requiredTags = Set.of(tagKey);
            return this;
        }

        /**
         * Convenience method: require any of a single tag.
         */
        public Builder anyOfTag(String tagKey) {
            this.anyOfTags = Set.of(tagKey);
            return this;
        }

        /** Builds the {@link ViewSubscriptionConfig}. */
        public ViewSubscriptionConfig build() {
            return new ViewSubscriptionConfig(viewName, eventTypes, requiredTags, anyOfTags);
        }
    }
}

