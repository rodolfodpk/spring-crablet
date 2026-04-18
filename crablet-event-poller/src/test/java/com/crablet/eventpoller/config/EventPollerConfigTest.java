package com.crablet.eventpoller.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventPollerConfig Unit Tests")
class EventPollerConfigTest {

    @Test
    @DisplayName("Should expose default values")
    void shouldExposeDefaultValues() {
        EventPollerConfig config = new EventPollerConfig();

        assertThat(config.getLeaderRetryCooldownMs()).isEqualTo(5000L);
        assertThat(config.getStartupDelayMs()).isEqualTo(500L);
        assertThat(config.getScheduler().getPoolSize()).isEqualTo(5);
        assertThat(config.getScheduler().getAwaitTerminationSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("Should set infrastructure values")
    void shouldSetInfrastructureValues() {
        EventPollerConfig config = new EventPollerConfig();
        EventPollerConfig.Scheduler scheduler = new EventPollerConfig.Scheduler();
        scheduler.setPoolSize(9);
        scheduler.setAwaitTerminationSeconds(12);

        config.setScheduler(scheduler);
        config.setLeaderRetryCooldownMs(111L);
        config.setStartupDelayMs(222L);

        assertThat(config.getScheduler()).isSameAs(scheduler);
        assertThat(config.getScheduler().getPoolSize()).isEqualTo(9);
        assertThat(config.getScheduler().getAwaitTerminationSeconds()).isEqualTo(12);
        assertThat(config.getLeaderRetryCooldownMs()).isEqualTo(111L);
        assertThat(config.getStartupDelayMs()).isEqualTo(222L);
    }
}
