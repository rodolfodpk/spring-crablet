package com.crablet.views;

import java.util.Set;

/**
 * Configuration for a view subscription.
 * Defines which events a view subscribes to by event type and/or tags.
 *
 * <p>Use {@link ViewProjector#subscription(String...)} for the common case (event types only).
 * Use {@link #builder(String)} when tag-based filtering is also needed.
 */
public class ViewSubscription {

    private final String viewName;
    private final Set<String> eventTypes;
    private final Set<String> requiredTags;
    private final Set<String> anyOfTags;

    // Per-view polling overrides — null means "use global default from ViewsConfig"
    private Long pollingIntervalMs;
    private Integer batchSize;
    private Boolean backoffEnabled;
    private Integer backoffThreshold;
    private Integer backoffMultiplier;
    private Integer backoffMaxSeconds;

    protected ViewSubscription(
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

    public Long getPollingIntervalMs() { return pollingIntervalMs; }
    public void setPollingIntervalMs(Long pollingIntervalMs) { this.pollingIntervalMs = pollingIntervalMs; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Boolean getBackoffEnabled() { return backoffEnabled; }
    public void setBackoffEnabled(Boolean backoffEnabled) { this.backoffEnabled = backoffEnabled; }

    public Integer getBackoffThreshold() { return backoffThreshold; }
    public void setBackoffThreshold(Integer backoffThreshold) { this.backoffThreshold = backoffThreshold; }

    public Integer getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(Integer backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }

    public Integer getBackoffMaxSeconds() { return backoffMaxSeconds; }
    public void setBackoffMaxSeconds(Integer backoffMaxSeconds) { this.backoffMaxSeconds = backoffMaxSeconds; }

    /** Entry point for building a subscription. Use {@code projector.subscription(eventTypes)} for the common case. */
    public static Builder builder(String viewName) {
        return new Builder(viewName);
    }

    public static class Builder {
        private final String viewName;
        private Set<String> eventTypes = Set.of();
        private Set<String> requiredTags = Set.of();
        private Set<String> anyOfTags = Set.of();
        private Long pollingIntervalMs;
        private Integer batchSize;
        private Boolean backoffEnabled;
        private Integer backoffThreshold;
        private Integer backoffMultiplier;
        private Integer backoffMaxSeconds;

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

        /** Convenience method: require a single tag. */
        public Builder requireTag(String tagKey) {
            this.requiredTags = Set.of(tagKey);
            return this;
        }

        /** Convenience method: require any of a single tag. */
        public Builder anyOfTag(String tagKey) {
            this.anyOfTags = Set.of(tagKey);
            return this;
        }

        /** Override polling interval for this view only (ms). Null = use global default. */
        public Builder pollingIntervalMs(long pollingIntervalMs) {
            this.pollingIntervalMs = pollingIntervalMs;
            return this;
        }

        /** Override batch size for this view only. Null = use global default. */
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /** Override backoff enabled for this view only. Null = use global default (true). */
        public Builder backoffEnabled(boolean backoffEnabled) {
            this.backoffEnabled = backoffEnabled;
            return this;
        }

        /** Override backoff threshold for this view only. Null = use global default. */
        public Builder backoffThreshold(int backoffThreshold) {
            this.backoffThreshold = backoffThreshold;
            return this;
        }

        /** Override backoff multiplier for this view only. Null = use global default. */
        public Builder backoffMultiplier(int backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /** Override max backoff seconds for this view only. Null = use global default. */
        public Builder backoffMaxSeconds(int backoffMaxSeconds) {
            this.backoffMaxSeconds = backoffMaxSeconds;
            return this;
        }

        /** Builds the {@link ViewSubscription}. */
        public ViewSubscription build() {
            ViewSubscription s = new ViewSubscription(viewName, eventTypes, requiredTags, anyOfTags);
            s.pollingIntervalMs = pollingIntervalMs;
            s.batchSize = batchSize;
            s.backoffEnabled = backoffEnabled;
            s.backoffThreshold = backoffThreshold;
            s.backoffMultiplier = backoffMultiplier;
            s.backoffMaxSeconds = backoffMaxSeconds;
            return s;
        }
    }
}
