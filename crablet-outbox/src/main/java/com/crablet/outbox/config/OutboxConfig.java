package com.crablet.outbox.config;

import com.crablet.outbox.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "crablet.outbox")
public class OutboxConfig {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxConfig.class);
    
    private boolean enabled = false;
    private int batchSize = 100;
    private int fetchSize = 100;  // PostgreSQL fetch size for result set streaming
    private long pollingIntervalMs = 1000;
    private int maxRetries = 3;
    private long retryDelayMs = 5000;
    private LockStrategy lockStrategy = LockStrategy.GLOBAL;
    
    /**
     * Heartbeat TTL in seconds. If a leader hasn't updated its heartbeat within this time,
     * it's considered dead and other instances can take over.
     * Default: 30 seconds (conservative to avoid false positives)
     */
    private int heartbeatTtlSeconds = 30;
    
    /**
     * Interval in milliseconds for retrying acquisition of new/abandoned pairs (PER_TOPIC_PUBLISHER mode).
     * Default: 30000ms (30 seconds) for production
     * Set to 1000ms (1 second) for faster testing
     */
    private long acquisitionRetryIntervalMs = 30_000;
    
    @Autowired
    private TopicConfigurationProperties topicConfigurationProperties;
    
    public enum LockStrategy {
        GLOBAL,              // Single lock for all publishers (default)
        PER_TOPIC_PUBLISHER  // One lock per (topic, publisher) pair (maximum scalability)
    }
    
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
    
    public LockStrategy getLockStrategy() { return lockStrategy; }
    public void setLockStrategy(LockStrategy lockStrategy) { 
        this.lockStrategy = lockStrategy; 
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
    
    public int getHeartbeatTtlSeconds() {
        return heartbeatTtlSeconds;
    }
    
    public void setHeartbeatTtlSeconds(int heartbeatTtlSeconds) {
        this.heartbeatTtlSeconds = heartbeatTtlSeconds;
    }
    
    public long getAcquisitionRetryIntervalMs() {
        return acquisitionRetryIntervalMs;
    }
    
    public void setAcquisitionRetryIntervalMs(long acquisitionRetryIntervalMs) {
        this.acquisitionRetryIntervalMs = acquisitionRetryIntervalMs;
    }
}
