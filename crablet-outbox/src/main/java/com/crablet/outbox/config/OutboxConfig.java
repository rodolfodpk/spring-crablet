package com.crablet.outbox.config;

import com.crablet.outbox.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration for Outbox behavior.
 * Users must define as @Bean:
 * <pre>{@code
 * @Bean
 * @ConfigurationProperties(prefix = "crablet.outbox")
 * public OutboxConfig outboxConfig() {
 *     return new OutboxConfig();
 * }
 * }</pre>
 */
@ConfigurationProperties(prefix = "crablet.outbox")
public class OutboxConfig {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxConfig.class);
    
    private boolean enabled = false;
    private int batchSize = 100;
    private int fetchSize = 100;  // PostgreSQL fetch size for result set streaming
    private long pollingIntervalMs = 1000;
    private int maxRetries = 3;
    private long retryDelayMs = 5000;
    
    @Autowired
    private TopicConfigurationProperties topicConfigurationProperties;
    
    // No longer need LockStrategy enum - only GLOBAL mode supported
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    
    public int getFetchSize() { return fetchSize; }
    public void setFetchSize(int fetchSize) { this.fetchSize = fetchSize; }
    
    public long getPollingIntervalMs() { return pollingIntervalMs; }
    public void setPollingIntervalMs(long pollingIntervalMs) { 
        this.pollingIntervalMs = pollingIntervalMs; 
    }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { 
        this.retryDelayMs = retryDelayMs; 
    }
    
    public Map<String, TopicConfig> getTopics() { 
        Map<String, TopicConfig> topics = topicConfigurationProperties.toTopicConfigs();
        
        // If no topics are configured, create a default one
        if (topics.isEmpty()) {
            log.info("No topics configured, creating default topic");
            TopicConfig defaultTopic = TopicConfig.builder("default").build();
            topics.put("default", defaultTopic);
        }
        
        return topics;
    }
    
    public void setTopics(Map<String, TopicConfig> topics) {
        // Topics are configured via TopicConfigurationProperties, not directly
        log.debug("setTopics called with {} topics", topics != null ? topics.size() : 0);
    }
}
