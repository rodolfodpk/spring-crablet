package com.crablet.automations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Declares which events trigger an {@link AutomationHandler}.
 * Register an {@code AutomationSubscription} bean for each automation, using the same
 * {@code automationName} as the corresponding {@link AutomationHandler} implementation.
 */
public class AutomationSubscription {

    private final String automationName;
    private final Set<String> eventTypes;
    private final Set<String> requiredTags;
    private final Set<String> anyOfTags;

    protected AutomationSubscription(
            String automationName,
            Set<String> eventTypes,
            Set<String> requiredTags,
            Set<String> anyOfTags) {
        this.automationName = automationName;
        this.eventTypes = eventTypes != null ? Set.copyOf(eventTypes) : Set.of();
        this.requiredTags = requiredTags != null ? Set.copyOf(requiredTags) : Set.of();
        this.anyOfTags = anyOfTags != null ? Set.copyOf(anyOfTags) : Set.of();
    }

    public String getAutomationName() { return automationName; }
    public Set<String> getEventTypes() { return eventTypes; }
    public Set<String> getRequiredTags() { return requiredTags; }
    public Set<String> getAnyOfTags() { return anyOfTags; }

    /** Entry point for building a subscription. Prefer {@code handler.subscription(eventTypes)} to avoid repeating the automation name. */
    public static Builder builder(String automationName) {
        return new Builder(automationName);
    }

    public static class Builder {
        private final String automationName;
        private Set<String> eventTypes = Set.of();
        private Set<String> requiredTags = Set.of();
        private Set<String> anyOfTags = Set.of();

        public Builder(String automationName) {
            this.automationName = automationName;
        }

        /** Filter by event types (Set overload). */
        public Builder eventTypes(Set<String> eventTypes) {
            this.eventTypes = eventTypes;
            return this;
        }

        /** Filter by event types (varargs overload). Use {@code type(MyEvent.class)} for type safety. */
        public Builder eventTypes(String... eventTypes) {
            this.eventTypes = new HashSet<>(Arrays.asList(eventTypes));
            return this;
        }

        /** ALL of these tag keys must be present on the event for it to be delivered. */
        public Builder requiredTags(String... tagKeys) {
            this.requiredTags = new HashSet<>(Arrays.asList(tagKeys));
            return this;
        }

        /** At least ONE of these tag keys must be present on the event for it to be delivered. */
        public Builder anyOfTags(String... tagKeys) {
            this.anyOfTags = new HashSet<>(Arrays.asList(tagKeys));
            return this;
        }

        /** Builds the {@link AutomationSubscription}. */
        public AutomationSubscription build() {
            return new AutomationSubscription(automationName, eventTypes, requiredTags, anyOfTags);
        }
    }
}
