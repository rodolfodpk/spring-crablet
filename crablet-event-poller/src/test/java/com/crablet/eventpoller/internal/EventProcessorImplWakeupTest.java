package com.crablet.eventpoller.internal;

import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSource;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Deterministic unit tests for EventProcessorImpl wakeup and lifecycle branches.
 * Uses CapturingTaskScheduler and CapturingWakeupSource to avoid real scheduling.
 */
class EventProcessorImplWakeupTest {

    private static final String PROC = "wakeup-proc";

    @Test
    void startRegistersSchedulersAndStartsWakeupSource() {
        var scheduler = new CapturingTaskScheduler();
        var wakeupSource = new CapturingWakeupSource();

        buildProcessor(Map.of(PROC, config(true)), scheduler, wakeupSource).start();

        assertThat(scheduler.fixedRateTasks).as("leader retry task").hasSize(1);
        assertThat(scheduler.immediateTasks).as("initial processor poll task").hasSize(1);
        assertThat(wakeupSource.started).isTrue();
    }

    @Test
    void allDisabledProcessorsSkipSchedulerAndWakeupInitialization() {
        var scheduler = new CapturingTaskScheduler();
        var wakeupSource = new CapturingWakeupSource();

        buildProcessor(Map.of(PROC, config(false)), scheduler, wakeupSource).start();

        assertThat(scheduler.fixedRateTasks).isEmpty();
        assertThat(scheduler.immediateTasks).isEmpty();
        assertThat(wakeupSource.started).isFalse();
    }

    @Test
    void wakeupTriggerSchedulesImmediatePollForEnabledProcessors() {
        var scheduler = new CapturingTaskScheduler();
        var wakeupSource = new CapturingWakeupSource();
        var processor = buildProcessor(Map.of(PROC, config(true)), scheduler, wakeupSource);
        processor.start();
        int before = scheduler.immediateTasks.size();

        wakeupSource.trigger();

        assertThat(scheduler.immediateTasks.size())
                .as("wakeup adds one immediate task per enabled processor")
                .isGreaterThan(before);
    }

    @Test
    void stopClosesWakeupSourceAndReleasesLeadership() {
        var scheduler = new CapturingTaskScheduler();
        var wakeupSource = new CapturingWakeupSource();
        var leader = new RecordingLeaderElector();
        var processor = buildProcessor(Map.of(PROC, config(true)), scheduler, wakeupSource, leader);

        processor.start();
        processor.stop();

        assertThat(wakeupSource.closed).isTrue();
        assertThat(leader.released).isTrue();
    }

    @Test
    void stopCancelsAllScheduledFutures() {
        var scheduler = new CapturingTaskScheduler();
        var wakeupSource = new CapturingWakeupSource();
        var processor = buildProcessor(Map.of(PROC, config(true)), scheduler, wakeupSource);

        processor.start();
        assertThat(scheduler.futures).isNotEmpty();

        processor.stop();

        assertThat(scheduler.futures).allMatch(ScheduledFuture::isCancelled);
    }

    @Test
    void wakeupAfterStopIsIgnored() {
        var scheduler = new CapturingTaskScheduler();
        var wakeupSource = new CapturingWakeupSource();
        var processor = buildProcessor(Map.of(PROC, config(true)), scheduler, wakeupSource);

        processor.start();
        processor.stop();
        int afterStop = scheduler.immediateTasks.size();

        wakeupSource.trigger();

        assertThat(scheduler.immediateTasks.size())
                .as("no new tasks scheduled after shutdown")
                .isEqualTo(afterStop);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EventProcessorImpl<SimpleProcessorConfig, String> buildProcessor(
            Map<String, SimpleProcessorConfig> configs,
            TaskScheduler scheduler,
            ProcessorWakeupSource wakeupSource) {
        return buildProcessor(configs, scheduler, wakeupSource, new AlwaysLeaderElector());
    }

    private EventProcessorImpl<SimpleProcessorConfig, String> buildProcessor(
            Map<String, SimpleProcessorConfig> configs,
            TaskScheduler scheduler,
            ProcessorWakeupSource wakeupSource,
            LeaderElector leaderElector) {
        return new EventProcessorImpl<>(
                configs,
                leaderElector,
                new StubProgressTracker(),
                new StubEventFetcher(),
                new StubEventHandler(),
                mock(DataSource.class),
                scheduler,
                mock(ApplicationEventPublisher.class),
                wakeupSource,
                0L,
                0L,
                ClockProvider.systemDefault()
        );
    }

    private SimpleProcessorConfig config(boolean enabled) {
        return new SimpleProcessorConfig(PROC, enabled);
    }

    // ── Test doubles ─────────────────────────────────────────────────────────

    record SimpleProcessorConfig(String id, boolean enabled) implements ProcessorConfig<String> {
        @Override public String getProcessorId() { return id; }
        @Override public long getPollingIntervalMs() { return 60_000L; }
        @Override public int getBatchSize() { return 10; }
        @Override public boolean isBackoffEnabled() { return false; }
        @Override public int getBackoffThreshold() { return 0; }
        @Override public int getBackoffMultiplier() { return 0; }
        @Override public int getBackoffMaxSeconds() { return 0; }
        @Override public boolean isEnabled() { return enabled; }
    }

    static class CapturingTaskScheduler implements TaskScheduler {
        final List<Runnable> fixedRateTasks = new ArrayList<>();
        final List<Runnable> immediateTasks = new ArrayList<>();
        final List<FakeScheduledFuture> futures = new ArrayList<>();

        @Override public ScheduledFuture<?> schedule(Runnable t, Trigger trigger) { return record(t, false); }
        @Override public ScheduledFuture<?> schedule(Runnable t, Instant startTime) { return record(t, true); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable t, Instant s, Duration p) { return record(t, false); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable t, Duration p) { return record(t, false); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable t, Instant s, Duration d) { return record(t, false); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable t, Duration d) { return record(t, false); }

        private ScheduledFuture<?> record(Runnable task, boolean immediate) {
            FakeScheduledFuture f = new FakeScheduledFuture();
            futures.add(f);
            (immediate ? immediateTasks : fixedRateTasks).add(task);
            return f;
        }

        static class FakeScheduledFuture implements ScheduledFuture<Void> {
            private volatile boolean cancelled;
            @Override public boolean cancel(boolean may) { cancelled = true; return true; }
            @Override public boolean isCancelled() { return cancelled; }
            @Override public boolean isDone() { return cancelled; }
            @Override public Void get() { return null; }
            @Override public Void get(long t, TimeUnit u) { return null; }
            @Override public long getDelay(TimeUnit u) { return 0; }
            @Override public int compareTo(java.util.concurrent.Delayed o) { return 0; }
        }
    }

    static class CapturingWakeupSource implements ProcessorWakeupSource {
        private Runnable onWakeup = () -> {};
        boolean started;
        boolean closed;

        @Override public void start(Runnable cb) { onWakeup = cb; started = true; }
        @Override public void close() { closed = true; }
        void trigger() { onWakeup.run(); }
    }

    static class AlwaysLeaderElector implements LeaderElector {
        @Override public boolean tryAcquireGlobalLeader() { return true; }
        @Override public void releaseGlobalLeader() {}
        @Override public boolean isGlobalLeader() { return true; }
        @Override public String getInstanceId() { return "test"; }
    }

    static class RecordingLeaderElector extends AlwaysLeaderElector {
        boolean released;
        @Override public void releaseGlobalLeader() { released = true; }
    }

    static class StubProgressTracker implements ProgressTracker<String> {
        @Override public long getLastPosition(@NonNull String id) { return 0L; }
        @Override public void updateProgress(@NonNull String id, long pos) {}
        @Override public void recordError(@NonNull String id, @Nullable String err, int max) {}
        @Override public void resetErrorCount(@NonNull String id) {}
        @Override public @NonNull ProcessorStatus getStatus(@NonNull String id) { return ProcessorStatus.ACTIVE; }
        @Override public void setStatus(@NonNull String id, @NonNull ProcessorStatus s) {}
        @Override public void autoRegister(@NonNull String id, @NonNull String instanceId) {}
    }

    static class StubEventFetcher implements EventFetcher<String> {
        @Override public @NonNull List<StoredEvent> fetchEvents(@NonNull String id, long pos, int batch) {
            return List.of();
        }
    }

    static class StubEventHandler implements EventHandler<String> {
        @Override public int handle(@NonNull String id, @NonNull List<StoredEvent> events) { return 0; }
    }
}
