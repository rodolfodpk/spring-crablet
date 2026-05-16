package com.crablet.eventpoller.config;

import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.PostgresNotifyWakeupSourceFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    @DisplayName("Should create platform thread scheduler from config by default")
    void shouldCreatePlatformThreadSchedulerFromConfigByDefault() {
        EventPollerConfig config = new EventPollerConfig();
        config.getScheduler().setPoolSize(2);
        config.getScheduler().setAwaitTerminationSeconds(7);
        MockEnvironment environment = new MockEnvironment();

        ThreadPoolTaskScheduler scheduler =
                (ThreadPoolTaskScheduler) autoConfiguration.taskScheduler(config, environment);

        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
        scheduler.shutdown();
    }

    @Test
    @DisplayName("Should create platform thread scheduler when Spring virtual threads are disabled")
    void shouldCreatePlatformThreadSchedulerWhenSpringVirtualThreadsAreDisabled() {
        EventPollerConfig config = new EventPollerConfig();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.threads.virtual.enabled", "false");

        TaskScheduler scheduler = autoConfiguration.taskScheduler(config, environment);

        assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        ((ThreadPoolTaskScheduler) scheduler).shutdown();
    }

    @Test
    @DisplayName("Should create virtual thread scheduler when Spring virtual threads are enabled")
    void shouldCreateVirtualThreadSchedulerWhenSpringVirtualThreadsAreEnabled() {
        EventPollerConfig config = new EventPollerConfig();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.threads.virtual.enabled", "true");

        TaskScheduler scheduler = autoConfiguration.taskScheduler(config, environment);

        assertThat(scheduler).isInstanceOf(SimpleAsyncTaskScheduler.class);
        ((SimpleAsyncTaskScheduler) scheduler).close();
    }

    @Test
    @DisplayName("Should run scheduled tasks on virtual threads when Spring virtual threads are enabled")
    void shouldRunScheduledTasksOnVirtualThreadsWhenSpringVirtualThreadsAreEnabled() throws Exception {
        EventPollerConfig config = new EventPollerConfig();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.threads.virtual.enabled", "true");
        SimpleAsyncTaskScheduler scheduler =
                (SimpleAsyncTaskScheduler) autoConfiguration.taskScheduler(config, environment);
        AtomicReference<Thread> executingThread = new AtomicReference<>();
        CountDownLatch taskRan = new CountDownLatch(1);

        try {
            scheduler.start();
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                executingThread.set(Thread.currentThread());
                taskRan.countDown();
            }, Instant.now());

            assertThat(future.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(taskRan.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isTrue();
            assertThat(executingThread.get()).isNotNull();
            assertThat(executingThread.get().isVirtual()).isTrue();
        } finally {
            scheduler.close();
        }
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
