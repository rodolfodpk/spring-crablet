package com.crablet.eventpoller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global configuration for the crablet event-poller infrastructure.
 */
@ConfigurationProperties(prefix = "crablet.event-poller")
public class EventPollerConfig {

    private Scheduler scheduler = new Scheduler();
    private long leaderRetryCooldownMs = 5000;
    private long startupDelayMs = 500;

    public static class Scheduler {
        private int poolSize = 5;
        private int awaitTerminationSeconds = 60;

        public int getPoolSize() { return poolSize; }
        public void setPoolSize(int poolSize) { this.poolSize = poolSize; }

        public int getAwaitTerminationSeconds() { return awaitTerminationSeconds; }
        public void setAwaitTerminationSeconds(int awaitTerminationSeconds) { this.awaitTerminationSeconds = awaitTerminationSeconds; }
    }

    public Scheduler getScheduler() { return scheduler; }
    public void setScheduler(Scheduler scheduler) { this.scheduler = scheduler; }

    public long getLeaderRetryCooldownMs() { return leaderRetryCooldownMs; }
    public void setLeaderRetryCooldownMs(long leaderRetryCooldownMs) { this.leaderRetryCooldownMs = leaderRetryCooldownMs; }

    public long getStartupDelayMs() { return startupDelayMs; }
    public void setStartupDelayMs(long startupDelayMs) { this.startupDelayMs = startupDelayMs; }
}
