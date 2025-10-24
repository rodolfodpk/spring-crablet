package com.crablet.outbox.impl;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "crablet.outbox.topics")
public class TopicConfigurationProperties {
    
    private static final Logger log = LoggerFactory.getLogger(TopicConfigurationProperties.class);
    
    private Map<String, TopicProperties> topics = new HashMap<>();
    
    public Map<String, TopicProperties> getTopics() {
        return topics;
    }
    
    public void setTopics(Map<String, TopicProperties> topics) {
        log.info("Setting topics: {}", topics);
        this.topics = topics;
    }
    
    @PostConstruct
    public void logConfiguration() {
        log.info("TopicConfigurationProperties initialized with {} topics: {}", topics.size(), topics.keySet());
        
        // If no topics are configured, create a default one
        if (topics.isEmpty()) {
            log.info("No topics configured, creating default topic");
            TopicProperties defaultProps = new TopicProperties();
            defaultProps.setRequiredTags(""); // Empty means match all events
            defaultProps.setPublishers("LogPublisher,TestPublisher"); // Include available publishers
            topics.put("default", defaultProps);
        }
        
        for (var entry : topics.entrySet()) {
            TopicProperties props = entry.getValue();
            log.info("  Topic '{}': requiredTags='{}', anyOfTags='{}', exactTags={}, publishers='{}'", 
                entry.getKey(), props.getRequiredTags(), props.getAnyOfTags(), props.getExactTags(), props.getPublishers());
        }
    }
    
    /**
     * Convert properties to TopicConfig objects.
     */
    public Map<String, TopicConfig> toTopicConfigs() {
        log.info("Converting {} topic properties to TopicConfig objects", topics.size());
        Map<String, TopicConfig> configs = new HashMap<>();
        
        for (var entry : topics.entrySet()) {
            String topicName = entry.getKey();
            TopicProperties props = entry.getValue();
            
            log.debug("Processing topic '{}': requiredTags='{}', anyOfTags='{}', exactTags={}", 
                topicName, props.getRequiredTags(), props.getAnyOfTags(), props.getExactTags());
            
            TopicConfig.Builder builder = TopicConfig.builder(topicName);
            
            // Add required tags
            if (props.getRequiredTags() != null && !props.getRequiredTags().trim().isEmpty()) {
                String[] tags = props.getRequiredTags().split(",");
                for (String tag : tags) {
                    if (!tag.trim().isEmpty()) {
                        builder.requireTag(tag.trim());
                    }
                }
            }
            
            // Add anyOf tags
            if (props.getAnyOfTags() != null && !props.getAnyOfTags().trim().isEmpty()) {
                String[] tags = props.getAnyOfTags().split(",");
                for (String tag : tags) {
                    if (!tag.trim().isEmpty()) {
                        builder.anyOfTag(tag.trim());
                    }
                }
            }
            
            // Add exact tags
            if (props.getExactTags() != null) {
                for (var exactTag : props.getExactTags().entrySet()) {
                    builder.exactTag(exactTag.getKey(), exactTag.getValue());
                }
            }
            
            // Add publishers
            if (props.getPublishers() != null && !props.getPublishers().trim().isEmpty()) {
                String[] publishers = props.getPublishers().split(",");
                for (String publisher : publishers) {
                    if (!publisher.trim().isEmpty()) {
                        builder.publisher(publisher.trim());
                    }
                }
            }
            
            TopicConfig config = builder.build();
            configs.put(topicName, config);
            log.debug("Created TopicConfig for '{}': {}", topicName, config);
        }
        
        log.info("Created {} TopicConfig objects", configs.size());
        return configs;
    }
    
    public static class TopicProperties {
        private String requiredTags;
        private String anyOfTags;
        private Map<String, String> exactTags;
        private String publishers;
        
        public String getRequiredTags() {
            return requiredTags;
        }
        
        public void setRequiredTags(String requiredTags) {
            this.requiredTags = requiredTags;
        }
        
        public String getAnyOfTags() {
            return anyOfTags;
        }
        
        public void setAnyOfTags(String anyOfTags) {
            this.anyOfTags = anyOfTags;
        }
        
        public Map<String, String> getExactTags() {
            return exactTags;
        }
        
        public void setExactTags(Map<String, String> exactTags) {
            this.exactTags = exactTags;
        }
        
        public String getPublishers() {
            return publishers;
        }

        public void setPublishers(String publishers) {
            this.publishers = publishers;
        }
    }
}
