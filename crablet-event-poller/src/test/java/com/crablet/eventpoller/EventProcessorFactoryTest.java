package com.crablet.eventpoller;

import com.crablet.eventpoller.config.EventPollerConfig;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSource;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSourceFactory;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

@DisplayName("EventProcessorFactory Unit Tests")
class EventProcessorFactoryTest {

    @Test
    @DisplayName("Should create processor from named spec with defaults")
    void shouldCreateProcessorFromSpecWithDefaults() {
        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                ProcessorSpec.<TestProcessorConfig, String>builder()
                        .configs(Map.of("p1", new TestProcessorConfig("p1")))
                        .leaderElector(mock(LeaderElector.class))
                        .progressTracker(mockProgressTracker())
                        .eventFetcher((processorId, lastPosition, batchSize) -> List.of())
                        .eventHandler((processorId, events) -> 0)
                        .taskScheduler(mock(TaskScheduler.class))
                        .eventPublisher(mock(ApplicationEventPublisher.class))
                        .build());

        assertThat(processor.getAllStatuses()).containsKey("p1");
    }

    @Test
    @DisplayName("Should reject spec without an election strategy")
    void shouldRejectSpecWithoutElectionStrategy() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                ProcessorSpec.<TestProcessorConfig, String>builder()
                        .configs(Map.of())
                        .progressTracker(mockProgressTracker())
                        .eventFetcher((processorId, lastPosition, batchSize) -> List.of())
                        .eventHandler((processorId, events) -> 0)
                        .taskScheduler(mock(TaskScheduler.class))
                        .eventPublisher(mock(ApplicationEventPublisher.class))
                        .build());
    }

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
    @DisplayName("Should create processor with explicit leader elector from event selections")
    void shouldCreateProcessorWithExplicitLeaderElectorFromSelections() {
        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                ProcessorSpec.<TestProcessorConfig, String>builder()
                        .configs(Map.of("p1", new TestProcessorConfig("p1")))
                        .leaderElector(mock(LeaderElector.class))
                        .progressTracker(mockProgressTracker())
                        .eventFetcher((processorId, lastPosition, batchSize) -> List.of())
                        .eventHandler((processorId, events) -> 0)
                        .taskScheduler(mock(TaskScheduler.class))
                        .eventPublisher(mock(ApplicationEventPublisher.class))
                        .wakeupSourceFactory(new CountingWakeupFactory())
                        .eventPollerConfig(new EventPollerConfig())
                        .selections(List.of(selection(Set.of("WalletOpened"), Set.of("wallet_id"), Set.of(), Map.of())))
                        .build());

        assertThat(processor).isNotNull();
        assertThat(processor.getAllStatuses()).containsKey("p1");
    }

    @Test
    @DisplayName("Should create processor and leader elector from processor name and event selections")
    void shouldCreateProcessorAndLeaderElectorFromSelections() {
        EventProcessor<TestProcessorConfig, String> processor = EventProcessorFactory.createProcessor(
                ProcessorSpec.<TestProcessorConfig, String>builder()
                        .configs(Map.of("p1", new TestProcessorConfig("p1")))
                        .processorName("test-module")
                        .lockKey(99L)
                        .instanceId("instance-1")
                        .writeDataSource(new WriteDataSource(mock(DataSource.class)))
                        .progressTracker(mockProgressTracker())
                        .eventFetcher((processorId, lastPosition, batchSize) -> List.of())
                        .eventHandler((processorId, events) -> 0)
                        .taskScheduler(mock(TaskScheduler.class))
                        .eventPublisher(mock(ApplicationEventPublisher.class))
                        .wakeupSourceFactory(new CountingWakeupFactory())
                        .eventPollerConfig(new EventPollerConfig())
                        .selections(List.of(selection(Set.of("WalletOpened"), Set.of(), Set.of(), Map.of())))
                        .build());

        assertThat(processor).isNotNull();
        assertThat(processor.getAllStatuses()).containsKey("p1");
    }

    @Test
    @DisplayName("Should reject partial raw election settings")
    void shouldRejectPartialRawElectionSettings() {
        assertThatIllegalArgumentException().isThrownBy(() -> baseSpecBuilder()
                .processorName("test-module")
                .build());
    }

    @Test
    @DisplayName("Should reject leader elector combined with raw election settings")
    void shouldRejectMixedElectionStrategies() {
        assertThatIllegalArgumentException().isThrownBy(() -> baseSpecBuilder()
                .leaderElector(mock(LeaderElector.class))
                .processorName("test-module")
                .build());
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

    private static ProcessorSpec.Builder<TestProcessorConfig, String> baseSpecBuilder() {
        return ProcessorSpec.<TestProcessorConfig, String>builder()
                .configs(Map.of())
                .progressTracker(mockProgressTracker())
                .eventFetcher((processorId, lastPosition, batchSize) -> List.of())
                .eventHandler((processorId, events) -> 0)
                .taskScheduler(mock(TaskScheduler.class))
                .eventPublisher(mock(ApplicationEventPublisher.class));
    }

    static class TestProgressTracker implements ProgressTracker<String> {
        @Override public long getLastPosition(String processorId) { return 0; }
        @Override public void updateProgress(String processorId, long position) {}
        @Override public void recordError(String processorId, @Nullable String error, int maxErrors) {}
        @Override public void resetErrorCount(String processorId) {}
        @Override public ProcessorStatus getStatus(String processorId) { return ProcessorStatus.ACTIVE; }
        @Override public void setStatus(String processorId, ProcessorStatus status) {}
        @Override public void autoRegister(String processorId, String instanceId) {}
    }

    static class TestProcessorConfig implements ProcessorConfig<String> {
        private final String id;

        TestProcessorConfig(String id) {
            this.id = id;
        }

        @Override public String getProcessorId() { return id; }
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
                @Override public void close(Runnable onWakeup) {}
                @Override public void close() {}
            };
        }
    }

    private static EventSelection selection(
            Set<String> types, Set<String> required, Set<String> anyOf, Map<String, String> exact) {
        return new EventSelection() {
            @Override public Set<String> getEventTypes()   { return types; }
            @Override public Set<String> getRequiredTags() { return required; }
            @Override public Set<String> getAnyOfTags()    { return anyOf; }
            @Override public Map<String, String> getExactTags() { return exact; }
        };
    }
}
