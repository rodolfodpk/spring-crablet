package com.crablet.views;

import com.crablet.views.config.ViewSubscriptionConfig;

import java.util.Set;

/**
 * User-friendly alias for ViewSubscriptionConfig.
 * Provides a cleaner API for configuring view subscriptions.
 * 
 * <p>This class is an alias that delegates to ViewSubscriptionConfig.
 * Users can use either ViewSubscription or ViewSubscriptionConfig.
 */
public class ViewSubscription extends ViewSubscriptionConfig {
    
    public ViewSubscription(
            String viewName,
            Set<String> eventTypes,
            Set<String> requiredTags,
            Set<String> anyOfTags) {
        super(viewName, eventTypes, requiredTags, anyOfTags);
    }
    
    public static Builder builder(String viewName) {
        return new Builder(viewName);
    }
    
    public static class Builder extends ViewSubscriptionConfig.Builder {
        
        public Builder(String viewName) {
            super(viewName);
        }
        
        @Override
        public Builder eventTypes(Set<String> eventTypes) {
            super.eventTypes(eventTypes);
            return this;
        }
        
        @Override
        public Builder eventTypes(String... eventTypes) {
            super.eventTypes(eventTypes);
            return this;
        }
        
        @Override
        public Builder requiredTags(Set<String> requiredTags) {
            super.requiredTags(requiredTags);
            return this;
        }
        
        @Override
        public Builder requiredTags(String... requiredTags) {
            super.requiredTags(requiredTags);
            return this;
        }
        
        @Override
        public Builder anyOfTags(Set<String> anyOfTags) {
            super.anyOfTags(anyOfTags);
            return this;
        }
        
        @Override
        public Builder anyOfTags(String... anyOfTags) {
            super.anyOfTags(anyOfTags);
            return this;
        }
        
        /**
         * Convenience method: require a single tag.
         */
        public Builder requireTag(String tagKey) {
            super.requireTag(tagKey);
            return this;
        }
        
        /**
         * Convenience method: require any of a single tag.
         */
        public Builder anyOfTag(String tagKey) {
            super.anyOfTag(tagKey);
            return this;
        }
        
        @Override
        public ViewSubscription build() {
            ViewSubscriptionConfig config = super.build();
            return new ViewSubscription(
                config.getViewName(),
                config.getEventTypes(),
                config.getRequiredTags(),
                config.getAnyOfTags()
            );
        }
    }
}

