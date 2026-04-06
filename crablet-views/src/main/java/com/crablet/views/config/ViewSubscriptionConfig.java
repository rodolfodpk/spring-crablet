package com.crablet.views.config;

import com.crablet.views.ViewSubscription;

import java.util.Set;

/**
 * @deprecated Use {@link ViewSubscription} instead.
 */
@Deprecated
public class ViewSubscriptionConfig extends ViewSubscription {

    protected ViewSubscriptionConfig(
            String viewName,
            Set<String> eventTypes,
            Set<String> requiredTags,
            Set<String> anyOfTags) {
        super(viewName, eventTypes, requiredTags, anyOfTags);
    }

    /** @deprecated Use {@link ViewSubscription#builder(String)} instead. */
    @Deprecated
    public static Builder builder(String viewName) {
        return new Builder(viewName);
    }

    /** @deprecated Use {@link ViewSubscription.Builder} instead. */
    @Deprecated
    public static class Builder extends ViewSubscription.Builder {

        public Builder(String viewName) {
            super(viewName);
        }

        @Override public Builder eventTypes(Set<String> eventTypes) { super.eventTypes(eventTypes); return this; }
        @Override public Builder eventTypes(String... eventTypes) { super.eventTypes(eventTypes); return this; }
        @Override public Builder requiredTags(Set<String> requiredTags) { super.requiredTags(requiredTags); return this; }
        @Override public Builder requiredTags(String... requiredTags) { super.requiredTags(requiredTags); return this; }
        @Override public Builder anyOfTags(Set<String> anyOfTags) { super.anyOfTags(anyOfTags); return this; }
        @Override public Builder anyOfTags(String... anyOfTags) { super.anyOfTags(anyOfTags); return this; }
        @Override public Builder requireTag(String tagKey) { super.requireTag(tagKey); return this; }
        @Override public Builder anyOfTag(String tagKey) { super.anyOfTag(tagKey); return this; }

        @Override
        public ViewSubscriptionConfig build() {
            ViewSubscription s = super.build();
            return new ViewSubscriptionConfig(s.getViewName(), s.getEventTypes(), s.getRequiredTags(), s.getAnyOfTags());
        }
    }
}
