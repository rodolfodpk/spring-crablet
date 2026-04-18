package com.crablet.automations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the automations module.
 */
@ConfigurationProperties(prefix = "crablet.automations")
public class AutomationsConfig {

    private boolean enabled = false;
    private long pollingIntervalMs = 1000L;
    private int batchSize = 100;
    private int backoffThreshold = 3;
    private int backoffMultiplier = 2;
    private int maxBackoffSeconds = 120;
    private long leaderElectionRetryIntervalMs = 30000L;

    private int fetchBatchSize = 1000;
    private int maxErrors = 10;

    private SharedFetch sharedFetch = new SharedFetch();

    public static class SharedFetch {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getPollingIntervalMs() { return pollingIntervalMs; }
    public void setPollingIntervalMs(long pollingIntervalMs) { this.pollingIntervalMs = pollingIntervalMs; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getBackoffThreshold() { return backoffThreshold; }
    public void setBackoffThreshold(int backoffThreshold) { this.backoffThreshold = backoffThreshold; }

    public int getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(int backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }

    public int getMaxBackoffSeconds() { return maxBackoffSeconds; }
    public void setMaxBackoffSeconds(int maxBackoffSeconds) { this.maxBackoffSeconds = maxBackoffSeconds; }

    public long getLeaderElectionRetryIntervalMs() { return leaderElectionRetryIntervalMs; }
    public void setLeaderElectionRetryIntervalMs(long ms) { this.leaderElectionRetryIntervalMs = ms; }

    public int getFetchBatchSize() { return fetchBatchSize; }
    public void setFetchBatchSize(int fetchBatchSize) { this.fetchBatchSize = fetchBatchSize; }

    public int getMaxErrors() { return maxErrors; }
    public void setMaxErrors(int maxErrors) { this.maxErrors = maxErrors; }

    public SharedFetch getSharedFetch() { return sharedFetch; }
    public void setSharedFetch(SharedFetch sharedFetch) { this.sharedFetch = sharedFetch; }
}
