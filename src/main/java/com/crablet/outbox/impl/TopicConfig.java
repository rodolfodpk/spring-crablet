package com.crablet.outbox.impl;

import java.util.*;

/**
 * Configuration for an outbox topic.
 * Defines which events belong to this topic based on tag keys.
 */
public class TopicConfig {
    private final String name;
    private final Set<String> requiredTags;       // ALL must be present
    private final Set<String> anyOfTags;          // At least ONE must be present
    private final Map<String, String> exactTags;  // Optional exact key=value matches
    private final Set<String> publishers;          // Explicit publisher list
    
    public TopicConfig(String name, Set<String> requiredTags, Set<String> anyOfTags, 
                       Map<String, String> exactTags, Set<String> publishers) {
        this.name = name;
        this.requiredTags = requiredTags != null ? requiredTags : Set.of();
        this.anyOfTags = anyOfTags != null ? anyOfTags : Set.of();
        this.exactTags = exactTags != null ? exactTags : Map.of();
        this.publishers = publishers != null ? publishers : Set.of();
    }
    
    public String getName() {
        return name;
    }
    
    public Set<String> getPublishers() {
        return publishers;
    }
    
    public Set<String> getRequiredTags() {
        return requiredTags;
    }
    
    public Set<String> getAnyOfTags() {
        return anyOfTags;
    }
    
    public Map<String, String> getExactTags() {
        return exactTags;
    }
    
    /**
     * Check if event tags match this topic's criteria.
     */
    public boolean matches(Map<String, String> eventTags) {
        // Check required tags (ALL must be present)
        if (!eventTags.keySet().containsAll(requiredTags)) {
            return false;
        }
        
        // Check anyOf tags (at least ONE must be present)
        if (!anyOfTags.isEmpty() && Collections.disjoint(eventTags.keySet(), anyOfTags)) {
            return false;
        }
        
        // Check exact matches (if specified)
        for (var entry : exactTags.entrySet()) {
            if (!entry.getValue().equals(eventTags.get(entry.getKey()))) {
                return false;
            }
        }
        
        return true;
    }
    
    public static Builder builder(String name) {
        return new Builder(name);
    }
    
    public static class Builder {
        private final String name;
        private Set<String> requiredTags = new HashSet<>();
        private Set<String> anyOfTags = new HashSet<>();
        private Map<String, String> exactTags = new HashMap<>();
        private Set<String> publishers = new HashSet<>();
        
        public Builder(String name) {
            this.name = name;
        }
        
        public Builder requireTag(String tag) {
            requiredTags.add(tag);
            return this;
        }
        
        public Builder requireTags(String... tags) {
            requiredTags.addAll(Arrays.asList(tags));
            return this;
        }
        
        public Builder anyOfTag(String tag) {
            anyOfTags.add(tag);
            return this;
        }
        
        public Builder anyOfTags(String... tags) {
            anyOfTags.addAll(Arrays.asList(tags));
            return this;
        }
        
        public Builder exactTag(String key, String value) {
            exactTags.put(key, value);
            return this;
        }
        
        public Builder publisher(String publisher) {
            publishers.add(publisher);
            return this;
        }
        
        public Builder publishers(String... publishers) {
            this.publishers.addAll(Arrays.asList(publishers));
            return this;
        }
        
        public TopicConfig build() {
            return new TopicConfig(name, requiredTags, anyOfTags, exactTags, publishers);
        }
    }
}
