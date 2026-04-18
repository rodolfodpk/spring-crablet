package com.crablet.eventpoller.internal.sharedfetch;

import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventSelection;
import com.crablet.eventpoller.integration.AbstractEventProcessorTest;
import com.crablet.eventpoller.internal.LeaderElectorImpl;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SharedFetchModuleProcessor covering the five priority cases
 * from the shared-fetch design plan:
 * 1. Handler failure mid-batch
 * 2. App restart with stale scannedPosition
 * 3. Resume after long pause with sparse events
 * 4. High volume ~80% irrelevant
 * 5. PAUSED processor
 */
@SpringBootTest(classes = SharedFetchModuleProcessorIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("SharedFetchModuleProcessor Integration Tests")
class SharedFetchModuleProcessorIntegrationTest extends AbstractEventProcessorTest {

    static final String MODULE = "test-module";
    static final String PROC_A = "processor-a";
    static final String PROC_B = "processor-b";

    @Autowired
    SharedFetchModuleProcessor<TestProcessorConfig, String> processor;

    @Autowired
    EventStore eventStore;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    @Autowired
    TestProgressTracker progressTracker;

    @Autowired
    TestEventHandler handlerA;

    @Autowired
    TestEventHandler handlerB;

    @Autowired
    ModuleScanProgressRepository moduleScanRepo;

    @Autowired
    ProcessorScanProgressRepository processorScanRepo;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
        cleanScanProgress(jdbcTemplate);
        progressTracker.reset();
        handlerA.reset();
        handlerB.reset();
        // Reload cursor state from DB (also clears catchingUpSet for test isolation).
        // Not calling stop() — NoopTaskScheduler prevents real background scheduling.
        processor.reloadCursorState();
    }

    // ── Case 1: Handler failure mid-batch ──────────────────────────────────────

    @Test
    @DisplayName("Handler failure leaves processor cursors unchanged; module cursor and healthy processor advance")
    void handlerFailureMidBatch_leavesFailingProcessorCursorsUnchanged() {
        appendEvents("TypeA", 3);
        appendEvents("TypeB", 2);

        handlerA.setShouldThrow(true);

        processor.runSharedCycle();

        // ProcessorA failed: both handledPosition and scannedPosition stay at 0
        assertThat(progressTracker.getLastPosition(PROC_A))
                .as("ProcessorA handledPosition should not advance on failure")
                .isEqualTo(0L);
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_A))
                .as("ProcessorA scannedPosition should not advance on failure")
                .isEqualTo(0L);

        // ProcessorB succeeded: both positions advance to end of window
        long windowEnd = maxPosition(jdbcTemplate);
        assertThat(progressTracker.getLastPosition(PROC_B))
                .as("ProcessorB handledPosition should advance")
                .isGreaterThan(0L);
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_B))
                .as("ProcessorB scannedPosition should reach window end")
                .isEqualTo(windowEnd);

        // Module cursor advances regardless of processor failure
        assertThat(moduleScanRepo.getScanPosition(MODULE))
                .as("Module cursor must advance past all fetched events")
                .isEqualTo(windowEnd);

        // ProcessorB handled exactly its 2 TypeB events
        assertThat(handlerB.getHandledCount()).isEqualTo(2);
    }

    // ── Case 2: App restart with stale scannedPosition ────────────────────────

    @Test
    @DisplayName("On restart with stale scannedPosition, processor catches up before contributing to shared window")
    void restartWithStaleScannedPosition_processorCatchesUpViaSharedCycle() {
        // Append 5 TypeA events, then advance module cursor as if a previous instance processed them
        appendEvents("TypeA", 5);
        long advancedCursor = maxPosition(jdbcTemplate);

        // Seed DB as if a previous run advanced the module cursor but the processor's
        // scannedPosition was never persisted (e.g. crash before upsert)
        moduleScanRepo.upsertScanPosition(MODULE, advancedCursor);
        // processorScanRepo has no row for processor-a → defaults to 0

        // Reload state into memory (simulates what doStart/onLeadershipAcquired does)
        processor.reloadCursorState();

        // Append 2 more TypeA events to give the shared cycle something new to fetch
        appendEvents("TypeA", 2);

        // Run the shared cycle
        // - processor-a should be in CATCHING_UP (scanned=0 < cursor=advancedCursor)
        //   so it's excluded from the main fan-out but its catch-up iteration runs
        // - the new 2 events become the new window
        processor.runSharedCycle();

        // After catch-up: processor-a's scannedPosition should be >= advancedCursor
        long scannedA = processorScanRepo.getScannedPosition(MODULE, PROC_A);
        assertThat(scannedA)
                .as("ProcessorA should have caught up through the stale window")
                .isGreaterThanOrEqualTo(advancedCursor);

        // ProcessorA was in CATCHING_UP for the 2 new events, but catch-up ran after
        // the main cycle — total handled >= original 5
        assertThat(handlerA.getHandledCount())
                .as("ProcessorA should handle the catch-up events")
                .isGreaterThan(0);
    }

    // ── Case 3: Resume after long pause with sparse events ────────────────────

    @Test
    @DisplayName("Resume after long pause with sparse matching events advances scannedPosition to moduleScanCursor")
    void resumeAfterLongPause_sparseEvents_scannedPositionJumpsToModuleCursor() {
        // Advance module cursor far ahead using TypeB events (processor-a won't match)
        appendEvents("TypeB", 10);
        long advancedCursor = maxPosition(jdbcTemplate);
        moduleScanRepo.upsertScanPosition(MODULE, advancedCursor);

        // Pause processor-a then resume it (resume triggers stale-position check)
        processor.pause(PROC_A);
        processor.resume(PROC_A);

        // Force state reload so in-memory reflects the DB
        processor.reloadCursorState();

        // Append one new TypeB event so the cycle has something to fetch
        appendEvents("TypeB", 1);

        // Run the shared cycle — processor-a is CATCHING_UP and no TypeA events exist
        processor.runSharedCycle();

        // Processor-a had no matching events in the stale range → scannedPosition jumps to cursor
        long scannedA = processorScanRepo.getScannedPosition(MODULE, PROC_A);
        assertThat(scannedA)
                .as("ProcessorA scannedPosition should jump to moduleScanCursor (sparse catch-up)")
                .isGreaterThanOrEqualTo(advancedCursor);

        // Handler for processor-a was never called (no TypeA events)
        assertThat(handlerA.getHandledCount())
                .as("No TypeA events exist so handler should not be called")
                .isEqualTo(0);
    }

    // ── Case 4: High volume, ~80% irrelevant ──────────────────────────────────

    @Test
    @DisplayName("High volume with 80% irrelevant events: module cursor advances, only matching events dispatched")
    void highVolume_80percentIrrelevant_onlyMatchingEventsDispatched() {
        // 8 TypeB events (irrelevant to processor-a), 2 TypeA events
        appendEvents("TypeB", 8);
        appendEvents("TypeA", 2);

        processor.runSharedCycle();

        long windowEnd = maxPosition(jdbcTemplate);

        // Module cursor advanced through all 10 events
        assertThat(moduleScanRepo.getScanPosition(MODULE))
                .as("Module cursor should advance through all 10 events")
                .isEqualTo(windowEnd);

        // Processor-a saw only the 2 TypeA events
        assertThat(handlerA.getHandledCount())
                .as("ProcessorA should handle only the 2 TypeA events")
                .isEqualTo(2);

        // Processor-b saw all 8 TypeB events
        assertThat(handlerB.getHandledCount())
                .as("ProcessorB should handle all 8 TypeB events")
                .isEqualTo(8);

        // Both processors' scannedPositions reach window end
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_A)).isEqualTo(windowEnd);
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_B)).isEqualTo(windowEnd);
    }

    @Test
    @DisplayName("Per-processor batch size override limits shared-fetch dispatch and catch-up")
    void perProcessorBatchSizeOverride_limitsSharedFetchDispatchAndCatchUp() {
        SharedFetchModuleProcessor<TestProcessorConfig, String> localProcessor = new SharedFetchModuleProcessor<>(
                Map.of(PROC_A, new TestProcessorConfig(PROC_A, 250L, 2)),
                Map.of(PROC_A, new TypeFilterSelection("TypeA")),
                MODULE,
                "test-instance",
                new AlwaysLeaderElector(),
                progressTracker,
                moduleScanRepo,
                processorScanRepo,
                (processorId, events) -> handlerA.handle(processorId, events),
                dataSource,
                1000,
                new NoopTaskScheduler(),
                new org.springframework.context.support.GenericApplicationContext(),
                Function.identity());
        localProcessor.reloadCursorState();

        appendEvents("TypeA", 5);

        localProcessor.runSharedCycle();

        long handledPosition = progressTracker.getLastPosition(PROC_A);
        long scannedPosition = processorScanRepo.getScannedPosition(MODULE, PROC_A);
        long windowEnd = maxPosition(jdbcTemplate);

        assertThat(handlerA.getHandledCount())
                .as("ProcessorA handles one main dispatch plus one catch-up iteration, each capped at 2")
                .isEqualTo(4);
        assertThat(handlerA.getHandled())
                .extracting(StoredEvent::position)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(handledPosition).isEqualTo(4L);
        assertThat(scannedPosition)
                .as("Partial dispatch scans only through the last safely dispatched event")
                .isEqualTo(4L);
        assertThat(moduleScanRepo.getScanPosition(MODULE))
                .as("The shared module cursor still advances through the full fetched window")
                .isEqualTo(windowEnd);
    }

    // ── Case 5: PAUSED processor ──────────────────────────────────────────────

    @Test
    @DisplayName("PAUSED processor: module cursor and active processor advance; paused processor positions frozen")
    void pausedProcessor_moduleCursorAdvances_pausedPositionsFrozen() {
        appendEvents("TypeA", 3);
        appendEvents("TypeB", 2);

        processor.pause(PROC_A);

        processor.runSharedCycle();

        long windowEnd = maxPosition(jdbcTemplate);

        // Module cursor advanced
        assertThat(moduleScanRepo.getScanPosition(MODULE))
                .as("Module cursor must advance even with paused processor")
                .isEqualTo(windowEnd);

        // Processor-a is paused: positions stay at 0
        assertThat(progressTracker.getLastPosition(PROC_A))
                .as("Paused processor handledPosition must stay frozen")
                .isEqualTo(0L);
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_A))
                .as("Paused processor scannedPosition must stay frozen")
                .isEqualTo(0L);
        assertThat(handlerA.getHandledCount())
                .as("Paused processor handler must not be called")
                .isEqualTo(0);

        // Processor-b (active) advanced normally
        assertThat(progressTracker.getLastPosition(PROC_B)).isGreaterThan(0L);
        assertThat(handlerB.getHandledCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("No selection routes all fetched events to processor")
    void noSelection_routesAllFetchedEventsToProcessor() {
        SharedFetchModuleProcessor<TestProcessorConfig, String> localProcessor = newProcessor(
                Map.of(PROC_A, new TestProcessorConfig(PROC_A)),
                Map.of(),
                new AlwaysLeaderElector(),
                new CapturingTaskScheduler(),
                new CapturingWakeupSource());
        localProcessor.reloadCursorState();

        appendEvents("TypeA", 2);
        appendEvents("TypeB", 3);

        localProcessor.runSharedCycle();

        assertThat(handlerA.getHandledCount()).isEqualTo(5);
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_A)).isEqualTo(maxPosition(jdbcTemplate));
    }

    @Test
    @DisplayName("All processors disabled skip shared-fetch startup")
    void allProcessorsDisabled_skipSharedFetchStartup() {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        CapturingWakeupSource wakeupSource = new CapturingWakeupSource();
        TestProcessorConfig disabled = new TestProcessorConfig(PROC_A, 1000L, 100, false);
        SharedFetchModuleProcessor<TestProcessorConfig, String> localProcessor = newProcessor(
                Map.of(PROC_A, disabled),
                Map.of(PROC_A, new TypeFilterSelection("TypeA")),
                new AlwaysLeaderElector(),
                scheduler,
                wakeupSource);

        localProcessor.start();

        assertThat(scheduler.fixedRateTasks).isEmpty();
        assertThat(wakeupSource.started).isFalse();
        assertThat(progressTracker.getLastPosition(PROC_A)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Follower schedules retry and starts shared cycle after leadership is acquired")
    void followerSchedulesRetryAndStartsSharedCycleAfterLeadershipIsAcquired() {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        EventuallyLeaderElector leaderElector = new EventuallyLeaderElector(false, true);
        SharedFetchModuleProcessor<TestProcessorConfig, String> localProcessor = newProcessor(
                Map.of(PROC_A, new TestProcessorConfig(PROC_A)),
                Map.of(PROC_A, new TypeFilterSelection("TypeA")),
                leaderElector,
                scheduler,
                new CapturingWakeupSource());

        localProcessor.start();
        assertThat(scheduler.fixedRateTasks).hasSize(1);

        scheduler.fixedRateTasks.get(0).run();

        assertThat(leaderElector.tryAcquireCalls).isEqualTo(2);
        assertThat(scheduler.fixedRateTasks).hasSize(2);
        assertThat(scheduler.futures.get(0).isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Stop cancels schedules, closes wakeup source, and releases leadership")
    void stopCancelsSchedulesClosesWakeupSourceAndReleasesLeadership() {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        RecordingLeaderElector leaderElector = new RecordingLeaderElector();
        CapturingWakeupSource wakeupSource = new CapturingWakeupSource();
        SharedFetchModuleProcessor<TestProcessorConfig, String> localProcessor = newProcessor(
                Map.of(PROC_A, new TestProcessorConfig(PROC_A)),
                Map.of(PROC_A, new TypeFilterSelection("TypeA")),
                leaderElector,
                scheduler,
                wakeupSource);

        localProcessor.start();
        assertThat(scheduler.futures).isNotEmpty();

        localProcessor.stop();

        assertThat(scheduler.futures).allMatch(ScheduledFuture::isCancelled);
        assertThat(wakeupSource.closed).isTrue();
        assertThat(leaderElector.released).isTrue();
    }

    @Test
    @DisplayName("Wakeup source schedules immediate poll while idle")
    void wakeupSourceSchedulesImmediatePollWhileIdle() {
        CapturingTaskScheduler scheduler = new CapturingTaskScheduler();
        CapturingWakeupSource wakeupSource = new CapturingWakeupSource();
        SharedFetchModuleProcessor<TestProcessorConfig, String> localProcessor = newProcessor(
                Map.of(PROC_A, new TestProcessorConfig(PROC_A)),
                Map.of(PROC_A, new TypeFilterSelection("TypeA")),
                new AlwaysLeaderElector(),
                scheduler,
                wakeupSource);
        localProcessor.start();

        wakeupSource.trigger();

        assertThat(scheduler.immediateTasks).hasSize(1);
    }

    @Test
    @DisplayName("Paused catch-up processor is removed without dispatch")
    void pausedCatchUpProcessorIsRemovedWithoutDispatch() {
        appendEvents("TypeA", 3);
        long advancedCursor = maxPosition(jdbcTemplate);
        moduleScanRepo.upsertScanPosition(MODULE, advancedCursor);

        SharedFetchModuleProcessor<TestProcessorConfig, String> localProcessor = newProcessor(
                Map.of(PROC_A, new TestProcessorConfig(PROC_A)),
                Map.of(PROC_A, new TypeFilterSelection("TypeA")),
                new AlwaysLeaderElector(),
                new CapturingTaskScheduler(),
                new CapturingWakeupSource());
        localProcessor.reloadCursorState();
        localProcessor.pause(PROC_A);

        appendEvents("TypeA", 1);
        localProcessor.runSharedCycle();

        assertThat(handlerA.getHandledCount()).isEqualTo(0);
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_A)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Repositories default, insert, update, and list scan positions")
    void repositoriesDefaultInsertUpdateAndListScanPositions() {
        assertThat(moduleScanRepo.getScanPosition("unknown-module")).isEqualTo(0L);
        assertThat(processorScanRepo.getScannedPosition(MODULE, "unknown-processor")).isEqualTo(0L);

        moduleScanRepo.upsertScanPosition(MODULE, 10L);
        moduleScanRepo.upsertScanPosition(MODULE, 20L);
        processorScanRepo.upsertScannedPosition(MODULE, PROC_A, 11L);
        processorScanRepo.upsertScannedPosition(MODULE, PROC_A, 21L);
        processorScanRepo.upsertScannedPosition(MODULE, PROC_B, 22L);

        assertThat(moduleScanRepo.getScanPosition(MODULE)).isEqualTo(20L);
        assertThat(processorScanRepo.getScannedPosition(MODULE, PROC_A)).isEqualTo(21L);
        assertThat(processorScanRepo.getAllScannedPositions(MODULE))
                .containsEntry(PROC_A, 21L)
                .containsEntry(PROC_B, 22L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void appendEvents(String type, int count) {
        List<AppendEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(AppendEvent.builder(type)
                    .data(("{\"i\":" + i + "}").getBytes())
                    .build());
        }
        eventStore.appendCommutative(events);
    }

    private SharedFetchModuleProcessor<TestProcessorConfig, String> newProcessor(
            Map<String, TestProcessorConfig> configs,
            Map<String, EventSelection> selections,
            LeaderElector leaderElector,
            TaskScheduler taskScheduler,
            CapturingWakeupSource wakeupSource) {
        return new SharedFetchModuleProcessor<>(
                configs,
                selections,
                MODULE,
                "test-instance",
                leaderElector,
                progressTracker,
                moduleScanRepo,
                processorScanRepo,
                (processorId, events) -> switch (processorId) {
                    case PROC_A -> handlerA.handle(processorId, events);
                    case PROC_B -> handlerB.handle(processorId, events);
                    default -> 0;
                },
                dataSource,
                1000,
                taskScheduler,
                new org.springframework.context.support.GenericApplicationContext(),
                Function.identity(),
                wakeupSource);
    }

    private long maxPosition(JdbcTemplate jdbc) {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(position), 0) FROM events", Long.class);
        return max != null ? max : 0L;
    }

    private void cleanScanProgress(JdbcTemplate jdbc) {
        try {
            jdbc.execute("TRUNCATE TABLE crablet_module_scan_progress");
            jdbc.execute("TRUNCATE TABLE crablet_processor_scan_progress");
        } catch (Exception ignored) {
        }
    }

    // ── Test infrastructure ───────────────────────────────────────────────────

    static class TestProcessorConfig implements ProcessorConfig<String> {
        private final String id;
        private final long pollingIntervalMs;
        private final int batchSize;

        TestProcessorConfig(String id) {
            this(id, 1000L, 100);
        }

        TestProcessorConfig(String id, long pollingIntervalMs, int batchSize) {
            this(id, pollingIntervalMs, batchSize, true);
        }

        TestProcessorConfig(String id, long pollingIntervalMs, int batchSize, boolean enabled) {
            this.id = id;
            this.pollingIntervalMs = pollingIntervalMs;
            this.batchSize = batchSize;
            this.enabled = enabled;
        }

        @Override public String getProcessorId() { return id; }
        @Override public long getPollingIntervalMs() { return pollingIntervalMs; }
        @Override public int getBatchSize() { return batchSize; }
        @Override public boolean isBackoffEnabled() { return false; }
        @Override public int getBackoffThreshold() { return 0; }
        @Override public int getBackoffMultiplier() { return 0; }
        @Override public int getBackoffMaxSeconds() { return 0; }
        private final boolean enabled;
        @Override public boolean isEnabled() { return enabled; }
    }

    static class TypeFilterSelection implements EventSelection {
        private final String type;
        TypeFilterSelection(String type) { this.type = type; }
        @Override public Set<String> getEventTypes() { return Set.of(type); }
    }

    static class TestProgressTracker implements ProgressTracker<String> {
        private final Map<String, Long> positions = new ConcurrentHashMap<>();
        private final Map<String, ProcessorStatus> statuses = new ConcurrentHashMap<>();
        private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();

        @Override public long getLastPosition(String id) { return positions.getOrDefault(id, 0L); }
        @Override public void updateProgress(String id, long pos) { positions.put(id, pos); }
        @Override public void recordError(String id, String err, int max) {
            int count = errorCounts.merge(id, 1, Integer::sum);
            if (count >= max) statuses.put(id, ProcessorStatus.FAILED);
        }
        @Override public void resetErrorCount(String id) { errorCounts.put(id, 0); }
        @Override public ProcessorStatus getStatus(String id) { return statuses.getOrDefault(id, ProcessorStatus.ACTIVE); }
        @Override public void setStatus(String id, ProcessorStatus s) { statuses.put(id, s); }
        @Override public void autoRegister(String id, String instanceId) { positions.putIfAbsent(id, 0L); }

        void reset() { positions.clear(); statuses.clear(); errorCounts.clear(); }
    }

    static class TestEventHandler implements EventHandler<String> {
        private final List<StoredEvent> handled = new ArrayList<>();
        private volatile boolean shouldThrow = false;

        @Override
        public int handle(String processorId, List<StoredEvent> events) throws Exception {
            if (shouldThrow) throw new RuntimeException("Simulated handler failure");
            handled.addAll(events);
            return events.size();
        }

        int getHandledCount() { return handled.size(); }
        List<StoredEvent> getHandled() { return List.copyOf(handled); }
        void setShouldThrow(boolean v) { this.shouldThrow = v; }
        void reset() { handled.clear(); shouldThrow = false; }
    }

    /** TaskScheduler that records submissions without running them. */
    static class CapturingTaskScheduler implements TaskScheduler {
        private final List<Runnable> fixedRateTasks = new ArrayList<>();
        private final List<Runnable> immediateTasks = new ArrayList<>();
        private final List<FakeScheduledFuture> futures = new ArrayList<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, org.springframework.scheduling.Trigger trigger) {
            return remember(task, false);
        }
        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            return remember(task, true);
        }
        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            return remember(task, false);
        }
        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            return remember(task, false);
        }
        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            return remember(task, false);
        }
        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            return remember(task, false);
        }

        private ScheduledFuture<?> remember(Runnable task, boolean immediate) {
            FakeScheduledFuture future = new FakeScheduledFuture();
            futures.add(future);
            if (immediate) {
                immediateTasks.add(task);
            } else {
                fixedRateTasks.add(task);
            }
            return future;
        }

        static class FakeScheduledFuture implements ScheduledFuture<Void> {
            private volatile boolean cancelled = false;
            @Override public boolean cancel(boolean mayInterruptIfRunning) { cancelled = true; return true; }
            @Override public boolean isCancelled() { return cancelled; }
            @Override public boolean isDone() { return cancelled; }
            @Override public Void get() { return null; }
            @Override public Void get(long timeout, java.util.concurrent.TimeUnit unit) { return null; }
            @Override public long getDelay(TimeUnit unit) { return 0; }
            @Override public int compareTo(java.util.concurrent.Delayed o) { return 0; }
        }
    }

    static class NoopTaskScheduler extends CapturingTaskScheduler {
    }

    /** LeaderElector that always considers itself the global leader. */
    static class AlwaysLeaderElector implements LeaderElector {
        @Override public boolean tryAcquireGlobalLeader() { return true; }
        @Override public void releaseGlobalLeader() {}
        @Override public boolean isGlobalLeader() { return true; }
        @Override public String getInstanceId() { return "test-instance"; }
    }

    static class RecordingLeaderElector extends AlwaysLeaderElector {
        boolean released;

        @Override
        public void releaseGlobalLeader() {
            released = true;
        }
    }

    static class EventuallyLeaderElector implements LeaderElector {
        private final boolean firstResult;
        private final boolean laterResult;
        int tryAcquireCalls;

        EventuallyLeaderElector(boolean firstResult, boolean laterResult) {
            this.firstResult = firstResult;
            this.laterResult = laterResult;
        }

        @Override
        public boolean tryAcquireGlobalLeader() {
            tryAcquireCalls++;
            return tryAcquireCalls == 1 ? firstResult : laterResult;
        }

        @Override public void releaseGlobalLeader() {}
        @Override public boolean isGlobalLeader() { return tryAcquireCalls > 1 && laterResult; }
        @Override public String getInstanceId() { return "test-instance"; }
    }

    static class CapturingWakeupSource implements com.crablet.eventpoller.wakeup.ProcessorWakeupSource {
        private Runnable onWakeup;
        private boolean started;
        private boolean closed;

        @Override
        public void start(Runnable onWakeup) {
            this.onWakeup = onWakeup;
            this.started = true;
        }

        @Override
        public void close() {
            this.closed = true;
        }

        void trigger() {
            onWakeup.run();
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            ds.setDriverClass(org.postgresql.Driver.class);
            ds.setUrl(AbstractEventProcessorTest.postgres.getJdbcUrl());
            ds.setUsername(AbstractEventProcessorTest.postgres.getUsername());
            ds.setPassword(AbstractEventProcessorTest.postgres.getPassword());
            return ds;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
            var flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public EventStore eventStore(
                DataSource dataSource,
                tools.jackson.databind.ObjectMapper objectMapper,
                com.crablet.eventstore.EventStoreConfig config,
                com.crablet.eventstore.ClockProvider clock,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new com.crablet.eventstore.internal.EventStoreImpl(
                    dataSource, dataSource, objectMapper, config, clock, eventPublisher);
        }

        @Bean
        public com.crablet.eventstore.EventStoreConfig eventStoreConfig() {
            return new com.crablet.eventstore.EventStoreConfig();
        }

        @Bean
        public com.crablet.eventstore.ClockProvider clockProvider() {
            return new com.crablet.eventstore.internal.ClockProviderImpl();
        }

        @Bean
        public tools.jackson.databind.ObjectMapper objectMapper() {
            return tools.jackson.databind.json.JsonMapper.builder()
                    .disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build();
        }

        @Bean
        public TaskScheduler taskScheduler() {
            return new NoopTaskScheduler();
        }

        @Bean
        public org.springframework.context.ApplicationEventPublisher applicationEventPublisher() {
            return new org.springframework.context.support.GenericApplicationContext();
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public ModuleScanProgressRepository moduleScanProgressRepository(DataSource dataSource) {
            return new ModuleScanProgressRepository(dataSource);
        }

        @Bean
        public ProcessorScanProgressRepository processorScanProgressRepository(DataSource dataSource) {
            return new ProcessorScanProgressRepository(dataSource);
        }

        @Bean
        public TestProgressTracker testProgressTracker() {
            return new TestProgressTracker();
        }

        @Bean("handlerA")
        public TestEventHandler handlerA() {
            return new TestEventHandler();
        }

        @Bean("handlerB")
        public TestEventHandler handlerB() {
            return new TestEventHandler();
        }

        @Bean
        public EventHandler<String> combinedEventHandler(
                @org.springframework.beans.factory.annotation.Qualifier("handlerA") TestEventHandler hA,
                @org.springframework.beans.factory.annotation.Qualifier("handlerB") TestEventHandler hB) {
            return (processorId, events) -> switch (processorId) {
                case PROC_A -> hA.handle(processorId, events);
                case PROC_B -> hB.handle(processorId, events);
                default -> 0;
            };
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public SharedFetchModuleProcessor<TestProcessorConfig, String> sharedFetchModuleProcessor(
                DataSource dataSource,
                TestProgressTracker progressTracker,
                EventHandler<String> combinedEventHandler,
                ModuleScanProgressRepository moduleScanRepo,
                ProcessorScanProgressRepository processorScanRepo,
                TaskScheduler taskScheduler,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {

            Map<String, TestProcessorConfig> configs = Map.of(
                    PROC_A, new TestProcessorConfig(PROC_A),
                    PROC_B, new TestProcessorConfig(PROC_B));

            Map<String, EventSelection> selections = Map.of(
                    PROC_A, new TypeFilterSelection("TypeA"),
                    PROC_B, new TypeFilterSelection("TypeB"));

            return new SharedFetchModuleProcessor<>(
                    configs,
                    selections,
                    MODULE,
                    "test-instance",
                    new AlwaysLeaderElector(),
                    progressTracker,
                    moduleScanRepo,
                    processorScanRepo,
                    combinedEventHandler,
                    dataSource,
                    1000,
                    taskScheduler,
                    eventPublisher,
                    Function.identity());
        }
    }
}
