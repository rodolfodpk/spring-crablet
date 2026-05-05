package com.crablet.eventpoller;

import com.crablet.eventpoller.config.EventPollerConfig;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSource;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSourceFactory;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("EventProcessorFactory Unit Tests")
class EventProcessorFactoryTest {

    @Test
    @DisplayName("Should create leader elector")
    void shouldCreateLeaderElector() {
        LeaderElector leaderElector = EventProcessorFactory.createLeaderElector(
                new WriteDataSource(mock(DataSource.class)),
                "test-processor",
                "instance-1",
                42L,
                mock(ApplicationEventPublisher.class));

        assertThat(leaderElector).isNotNull();
        assertThat(leaderElector.getInstanceId()).isEqualTo("instance-1");
    }

    @Test
    @DisplayName("Should create processor with explicit leader elector and default wakeup")
    void shouldCreateProcessorWithExplicitLeaderElectorAndDefaultWakeup() {
        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                Map.of("p1", new TestProcessorConfig("p1")),
                mock(LeaderElector.class),
                mockProgressTracker(),
                (processorId, lastPosition, batchSize) -> List.of(),
                (processorId, events) -> 0,
                new WriteDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class));

        assertThat(processor).isNotNull();
        assertThat(processor.getAllStatuses()).containsKey("p1");
    }

    @Test
    @DisplayName("Should create processor with explicit wakeup and event poller config")
    void shouldCreateProcessorWithExplicitWakeupAndConfig() {
        CountingWakeupFactory wakeupFactory = new CountingWakeupFactory();
        EventPollerConfig config = new EventPollerConfig();
        config.setLeaderRetryCooldownMs(123L);
        config.setStartupDelayMs(456L);

        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                Map.of("p1", new TestProcessorConfig("p1")),
                mock(LeaderElector.class),
                mockProgressTracker(),
                (processorId, lastPosition, batchSize) -> List.of(),
                (processorId, events) -> 0,
                new WriteDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class),
                wakeupFactory,
                config);

        assertThat(processor).isNotNull();
        assertThat(wakeupFactory.created).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create processor and leader elector from processor name")
    void shouldCreateProcessorAndLeaderElectorFromProcessorName() {
        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                Map.of("p1", new TestProcessorConfig("p1")),
                "test-module",
                99L,
                "instance-1",
                mockProgressTracker(),
                (processorId, lastPosition, batchSize) -> List.of(),
                (processorId, events) -> 0,
                new WriteDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class),
                new CountingWakeupFactory(),
                new EventPollerConfig());

        assertThat(processor).isNotNull();
    }

    @Test
    @DisplayName("Should create processor from processor name with default wakeup")
    void shouldCreateProcessorFromProcessorNameWithDefaultWakeup() {
        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                Map.of("p1", new TestProcessorConfig("p1")),
                "test-module",
                99L,
                "instance-1",
                mockProgressTracker(),
                (processorId, lastPosition, batchSize) -> List.of(),
                (processorId, events) -> 0,
                new WriteDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class));

        assertThat(processor).isNotNull();
    }

    @Test
    @DisplayName("Should create processor from processor name with explicit wakeup and default config")
    void shouldCreateProcessorFromProcessorNameWithExplicitWakeupAndDefaultConfig() {
        CountingWakeupFactory wakeupFactory = new CountingWakeupFactory();

        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                Map.of("p1", new TestProcessorConfig("p1")),
                "test-module",
                99L,
                "instance-1",
                mockProgressTracker(),
                (processorId, lastPosition, batchSize) -> List.of(),
                (processorId, events) -> 0,
                new WriteDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class),
                wakeupFactory);

        assertThat(processor).isNotNull();
        assertThat(wakeupFactory.created).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create management service")
    void shouldCreateManagementService() {
        ProcessorManagementService<String> service = EventProcessorFactory.createManagementService(
                mock(EventProcessor.class),
                mockProgressTracker(),
                new ReadDataSource(mock(DataSource.class)));

        assertThat(service).isNotNull();
    }

    private static ProgressTracker<String> mockProgressTracker() {
        return new TestProgressTracker();
    }

    static class TestProgressTracker implements ProgressTracker<String> {
        @Override public long getLastPosition(@NonNull String processorId) { return 0; }
        @Override public void updateProgress(@NonNull String processorId, long position) {}
        @Override public void recordError(@NonNull String processorId, @Nullable String error, int maxErrors) {}
        @Override public void resetErrorCount(@NonNull String processorId) {}
        @Override public @NonNull ProcessorStatus getStatus(@NonNull String processorId) { return ProcessorStatus.ACTIVE; }
        @Override public void setStatus(@NonNull String processorId, @NonNull ProcessorStatus status) {}
        @Override public void autoRegister(@NonNull String processorId, @NonNull String instanceId) {}
    }

    static class TestProcessorConfig implements ProcessorConfig<String> {
        private final String id;

        TestProcessorConfig(String id) {
            this.id = id;
        }

        @Override public @NonNull String getProcessorId() { return id; }
        @Override public long getPollingIntervalMs() { return 1000L; }
        @Override public int getBatchSize() { return 100; }
        @Override public boolean isBackoffEnabled() { return true; }
        @Override public int getBackoffThreshold() { return 3; }
        @Override public int getBackoffMultiplier() { return 2; }
        @Override public int getBackoffMaxSeconds() { return 120; }
        @Override public boolean isEnabled() { return true; }
    }

    static class CountingWakeupFactory implements ProcessorWakeupSourceFactory {
        int created;

        @Override
        public ProcessorWakeupSource create() {
            created++;
            return new ProcessorWakeupSource() {
                @Override public void start(Runnable onWakeup) {}
                @Override public void close() {}
            };
        }
    }
}
