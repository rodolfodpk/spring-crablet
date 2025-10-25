package com.crablet.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for global outbox statistics tracking.
 * 
 * This configuration controls the global statistics publisher that monitors
 * all events processed across all topics and publishers in the outbox system.
 */
@Component
@ConfigurationProperties(prefix = "crablet.outbox.global-statistics")
public class GlobalStatisticsConfig {
    
    /**
     * Whether global statistics tracking is enabled.
     * Default: true (enabled by default for production monitoring)
     */
    private boolean enabled = true;
    
    /**
     * Interval in seconds between statistics log outputs.
     * Default: 30 seconds
     */
    private long logIntervalSeconds = 30;
    
    /**
     * Log level for statistics output.
     * Default: INFO
     */
    private String logLevel = "INFO";
    
    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getLogIntervalSeconds() {
        return logIntervalSeconds;
    }
    
    public void setLogIntervalSeconds(long logIntervalSeconds) {
        this.logIntervalSeconds = logIntervalSeconds;
    }
    
    public String getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
}
