package com.crablet.views.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for views module.
 */
@ConfigurationProperties(prefix = "crablet.views")
public class ViewsConfig {
    
    private boolean enabled = false;
    private long pollingIntervalMs = 1000L;
    private int batchSize = 100;
    private int backoffThreshold = 10;
    private int backoffMultiplier = 2;
    private int maxBackoffSeconds = 60;
    private long leaderElectionRetryIntervalMs = 30000L;
    
    /**
     * Map of view name to subscription configuration.
     */
    private Map<String, ViewSubscriptionConfig> subscriptions = new HashMap<>();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }
    
    public void setPollingIntervalMs(long pollingIntervalMs) {
        this.pollingIntervalMs = pollingIntervalMs;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    public int getBackoffThreshold() {
        return backoffThreshold;
    }
    
    public void setBackoffThreshold(int backoffThreshold) {
        this.backoffThreshold = backoffThreshold;
    }
    
    public int getBackoffMultiplier() {
        return backoffMultiplier;
    }
    
    public void setBackoffMultiplier(int backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }
    
    public int getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }
    
    public void setMaxBackoffSeconds(int maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }
    
    public long getLeaderElectionRetryIntervalMs() {
        return leaderElectionRetryIntervalMs;
    }
    
    public void setLeaderElectionRetryIntervalMs(long leaderElectionRetryIntervalMs) {
        this.leaderElectionRetryIntervalMs = leaderElectionRetryIntervalMs;
    }
    
    public Map<String, ViewSubscriptionConfig> getSubscriptions() {
        return subscriptions;
    }
    
    public void setSubscriptions(Map<String, ViewSubscriptionConfig> subscriptions) {
        this.subscriptions = subscriptions != null ? subscriptions : new HashMap<>();
    }
}

