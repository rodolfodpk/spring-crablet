package com.crablet.eventpoller.config;

import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.PostgresNotifyWakeupSourceFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventPollerAutoConfiguration Unit Tests")
class EventPollerAutoConfigurationTest {

    private final EventPollerAutoConfiguration autoConfiguration = new EventPollerAutoConfiguration();

    @Test
    @DisplayName("Should create instance id provider")
    void shouldCreateInstanceIdProvider() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("crablet.instance.id", "test-instance");

        InstanceIdProvider provider = autoConfiguration.instanceIdProvider(environment);

        assertThat(provider.getInstanceId()).isEqualTo("test-instance");
    }

    @Test
    @DisplayName("Should create default event poller config")
    void shouldCreateDefaultEventPollerConfig() {
        EventPollerConfig config = autoConfiguration.eventPollerConfig();

        assertThat(config.getLeaderRetryCooldownMs()).isEqualTo(5000L);
        assertThat(config.getStartupDelayMs()).isEqualTo(500L);
        assertThat(config.getScheduler().getPoolSize()).isEqualTo(5);
        assertThat(config.getScheduler().getAwaitTerminationSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("Should create task scheduler from config")
    void shouldCreateTaskSchedulerFromConfig() {
        EventPollerConfig config = new EventPollerConfig();
        config.getScheduler().setPoolSize(2);
        config.getScheduler().setAwaitTerminationSeconds(7);

        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) autoConfiguration.taskScheduler(config);

        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
        scheduler.shutdown();
    }

    @Test
    @DisplayName("Should use noop wakeup source factory when jdbc url is absent")
    void shouldUseNoopWakeupSourceFactoryWhenJdbcUrlIsAbsent() {
        EventPollerNotificationProperties properties = new EventPollerNotificationProperties();

        assertThat(autoConfiguration.processorWakeupSourceFactory(properties))
                .isInstanceOf(NoopProcessorWakeupSourceFactory.class);
    }

    @Test
    @DisplayName("Should use noop wakeup source factory when jdbc url is blank")
    void shouldUseNoopWakeupSourceFactoryWhenJdbcUrlIsBlank() {
        EventPollerNotificationProperties properties = new EventPollerNotificationProperties();
        properties.setJdbcUrl("  ");

        assertThat(autoConfiguration.processorWakeupSourceFactory(properties))
                .isInstanceOf(NoopProcessorWakeupSourceFactory.class);
    }

    @Test
    @DisplayName("Should create Postgres notify wakeup source factory when jdbc url is configured")
    void shouldCreatePostgresNotifyWakeupSourceFactoryWhenJdbcUrlIsConfigured() {
        EventPollerNotificationProperties properties = new EventPollerNotificationProperties();
        properties.setJdbcUrl("jdbc:postgresql://localhost:5432/app");
        properties.setUsername("user");
        properties.setPassword("secret");
        properties.setChannel("events");

        assertThat(autoConfiguration.processorWakeupSourceFactory(properties))
                .isInstanceOf(PostgresNotifyWakeupSourceFactory.class);
    }
}
