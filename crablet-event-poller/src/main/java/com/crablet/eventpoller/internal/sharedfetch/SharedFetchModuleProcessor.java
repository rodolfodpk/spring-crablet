package com.crablet.eventpoller.internal.sharedfetch;

import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventSelection;
import com.crablet.eventpoller.EventSelectionMatcher;
import com.crablet.eventpoller.internal.BackoffState;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSource;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSource;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Shared-fetch implementation of {@link EventProcessor}.
 *
 * <p>Instead of one DB query per processor per cycle, this processor issues a single
 * position-only fetch per module cycle and routes events in memory to each processor
 * via {@link EventSelectionMatcher}. This eliminates the N-query thundering herd that
 * occurs when every LISTEN/NOTIFY wakeup triggers N concurrent fetches.
 *
 * <p>Enabled via {@code crablet.views.shared-fetch.enabled=true} (or the equivalent
 * module flag). The legacy per-processor path remains the default.
 *
 * @param <C> processor configuration type
 * @param <I> processor identifier type
 */
public class SharedFetchModuleProcessor<C extends ProcessorConfig<I>, I>
        implements EventProcessor<C, I>, BackoffInfoProvider<I> {

    private static final Logger log = LoggerFactory.getLogger(SharedFetchModuleProcessor.class);

    private final Map<I, C> configs;
    private final Map<I, EventSelection> selections;
    private final String moduleName;
    private final String instanceId;
    private final LeaderElector leaderElector;
    private final ProgressTracker<I> progressTracker;
    private final ModuleScanProgressRepository moduleScanRepo;
    private final ProcessorScanProgressRepository processorScanRepo;
    private final EventHandler<I> eventHandler;
    private final DataSource readDataSource;
    private final int fetchBatchSize;
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher eventPublisher;
    private final Function<I, String> idSerializer;
    private final ProcessorWakeupSource wakeupSource;
    private final long pollingIntervalMs;

    private final Set<I> catchingUpSet = ConcurrentHashMap.newKeySet();
    private final Map<I, Long> inMemoryScannedPositions = new ConcurrentHashMap<>();
    private volatile long moduleScanCursor = 0L;
    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);
    private final BackoffState moduleBackoff;

    private volatile boolean schedulersInitialized = false;
    private volatile boolean shuttingDown = false;
    private final Object lifecycleMonitor = new Object();
    private volatile ScheduledFuture<?> sharedSchedule;
    private volatile ScheduledFuture<?> leaderRetrySchedule;

    public SharedFetchModuleProcessor(
            Map<I, C> configs,
            Map<I, EventSelection> selections,
            String moduleName,
            String instanceId,
            LeaderElector leaderElector,
            ProgressTracker<I> progressTracker,
            ModuleScanProgressRepository moduleScanRepo,
            ProcessorScanProgressRepository processorScanRepo,
            EventHandler<I> eventHandler,
            DataSource readDataSource,
            int fetchBatchSize,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            Function<I, String> idSerializer) {
        this(configs, selections, moduleName, instanceId, leaderElector, progressTracker,
                moduleScanRepo, processorScanRepo, eventHandler, readDataSource, fetchBatchSize,
                taskScheduler, eventPublisher, idSerializer, new NoopProcessorWakeupSource());
    }

    public SharedFetchModuleProcessor(
            Map<I, C> configs,
            Map<I, EventSelection> selections,
            String moduleName,
            String instanceId,
            LeaderElector leaderElector,
            ProgressTracker<I> progressTracker,
            ModuleScanProgressRepository moduleScanRepo,
            ProcessorScanProgressRepository processorScanRepo,
            EventHandler<I> eventHandler,
            DataSource readDataSource,
            int fetchBatchSize,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            Function<I, String> idSerializer,
            ProcessorWakeupSource wakeupSource) {
        this.configs = configs;
        this.selections = selections;
        this.moduleName = moduleName;
        this.instanceId = instanceId;
        this.leaderElector = leaderElector;
        this.progressTracker = progressTracker;
        this.moduleScanRepo = moduleScanRepo;
        this.processorScanRepo = processorScanRepo;
        this.eventHandler = eventHandler;
        this.readDataSource = readDataSource;
        this.fetchBatchSize = fetchBatchSize;
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
        this.idSerializer = idSerializer;
        this.wakeupSource = wakeupSource;

        this.pollingIntervalMs = configs.values().stream()
                .filter(ProcessorConfig::isEnabled)
                .mapToLong(ProcessorConfig::getPollingIntervalMs)
                .min()
                .orElse(1000L);

        C firstEnabled = configs.values().stream()
                .filter(ProcessorConfig::isEnabled)
                .findFirst()
                .orElse(null);
        this.moduleBackoff = firstEnabled != null && firstEnabled.isBackoffEnabled()
                ? new BackoffState(firstEnabled.getBackoffThreshold(), firstEnabled.getBackoffMultiplier(),
                        pollingIntervalMs, firstEnabled.getBackoffMaxSeconds())
                : new BackoffState(Integer.MAX_VALUE, 1, pollingIntervalMs, 0);
    }

    @PostConstruct
    public void postConstruct() {
        log.debug("[SharedFetchModuleProcessor:{}] Bean created. Waiting for Spring lifecycle event.", moduleName);
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed(ContextRefreshedEvent event) {
        initializeSchedulersIfNeeded("ContextRefreshedEvent");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        initializeSchedulersIfNeeded("ApplicationReadyEvent");
    }

    private void initializeSchedulersIfNeeded(String trigger) {
        synchronized (lifecycleMonitor) {
            if (schedulersInitialized) {
                log.debug("[{}] Already initialized, skipping {}", moduleName, trigger);
                return;
            }

            boolean anyEnabled = configs.values().stream().anyMatch(ProcessorConfig::isEnabled);
            if (!anyEnabled) {
                log.info("[{}] No processors enabled, skipping shared-fetch initialization", moduleName);
                schedulersInitialized = true;
                return;
            }

            try {
                shuttingDown = false;
                log.info("[{}] Starting shared-fetch loop via {}", moduleName, trigger);
                doStart();
                wakeupSource.start(this::requestImmediatePoll);
                schedulersInitialized = true;
                log.info("[{}] Shared-fetch loop started", moduleName);
            } catch (Exception e) {
                log.error("[{}] Failed to start shared-fetch loop via {}", moduleName, trigger, e);
            }
        }
    }

    private void doStart() {
        for (var entry : configs.entrySet()) {
            if (entry.getValue().isEnabled()) {
                progressTracker.autoRegister(entry.getKey(), instanceId);
            }
        }

        reloadCursorState();
        checkStalePositions();

        boolean acquired = leaderElector.tryAcquireGlobalLeader();
        if (acquired) {
            onLeadershipAcquired();
        } else {
            long retryInterval = configs.values().stream()
                    .filter(ProcessorConfig::isEnabled)
                    .findFirst()
                    .map(ProcessorConfig::getLeaderElectionRetryIntervalMs)
                    .orElse(30000L);
            leaderRetrySchedule = taskScheduler.scheduleAtFixedRate(
                    this::leaderRetryTask,
                    Duration.ofMillis(retryInterval));
            log.info("[{}] Not leader — retry scheduled at {}ms", moduleName, retryInterval);
        }
    }

    private void onLeadershipAcquired() {
        reloadCursorState();
        checkStalePositions();

        if (leaderRetrySchedule != null) {
            leaderRetrySchedule.cancel(false);
            leaderRetrySchedule = null;
        }

        sharedSchedule = taskScheduler.scheduleAtFixedRate(
                this::runSharedCycle,
                Duration.ofMillis(pollingIntervalMs));
        log.info("[{}] Leadership acquired — shared-fetch cycle at {}ms", moduleName, pollingIntervalMs);
    }

    private void leaderRetryTask() {
        if (shuttingDown) return;
        if (!leaderElector.isGlobalLeader()) {
            boolean acquired = leaderElector.tryAcquireGlobalLeader();
            if (acquired) {
                log.info("[{}] Leadership acquired after retry", moduleName);
                onLeadershipAcquired();
            }
        }
    }

    void reloadCursorState() {
        catchingUpSet.clear();
        moduleScanCursor = moduleScanRepo.getScanPosition(moduleName);
        Map<String, Long> allPositions = processorScanRepo.getAllScannedPositions(moduleName);
        inMemoryScannedPositions.clear();
        for (I id : configs.keySet()) {
            String serializedId = idSerializer.apply(id);
            inMemoryScannedPositions.put(id, allPositions.getOrDefault(serializedId, 0L));
        }
        log.debug("[{}] Cursor state reloaded: moduleScanCursor={}", moduleName, moduleScanCursor);
    }

    private void checkStalePositions() {
        for (var entry : configs.entrySet()) {
            I id = entry.getKey();
            if (!entry.getValue().isEnabled()) continue;
            if (progressTracker.getStatus(id) != ProcessorStatus.ACTIVE) continue;
            if (catchingUpSet.contains(id)) continue;
            long scanned = inMemoryScannedPositions.getOrDefault(id, 0L);
            if (scanned < moduleScanCursor) {
                catchingUpSet.add(id);
                log.debug("[{}] Processor {} is stale (scanned={} < cursor={}) — CATCHING_UP",
                        moduleName, id, scanned, moduleScanCursor);
            }
        }
    }

    void runSharedCycle() {
        if (shuttingDown) return;
        if (!cycleRunning.compareAndSet(false, true)) return;

        try {
            if (!leaderElector.isGlobalLeader()) {
                boolean acquired = leaderElector.tryAcquireGlobalLeader();
                if (acquired) {
                    log.info("[{}] Leadership acquired during cycle check", moduleName);
                    onLeadershipAcquired();
                }
                return;
            }

            checkStalePositions();

            List<StoredEvent> events = fetchPositionOnly(moduleScanCursor, fetchBatchSize);

            if (events.isEmpty()) {
                moduleBackoff.recordEmpty();
                return;
            }

            long windowEnd = events.get(events.size() - 1).position();
            boolean anyDispatched = false;

            for (var entry : configs.entrySet()) {
                I id = entry.getKey();
                C config = entry.getValue();
                if (!config.isEnabled()) continue;
                if (progressTracker.getStatus(id) != ProcessorStatus.ACTIVE) continue;
                if (catchingUpSet.contains(id)) continue;

                EventSelection selection = selections.get(id);
                List<StoredEvent> matched = selection != null
                        ? events.stream().filter(e -> EventSelectionMatcher.matches(selection, e)).toList()
                        : events;

                long currentHandled = progressTracker.getLastPosition(id);
                long currentScanned = inMemoryScannedPositions.getOrDefault(id, 0L);

                DispatchOutcome outcome = dispatch(id, matched, config.getBatchSize());
                CursorUpdate update = ProcessorCursorStateMachine.compute(
                        currentHandled, currentScanned, windowEnd, outcome);

                if (update.newHandledPosition() != currentHandled) {
                    progressTracker.updateProgress(id, update.newHandledPosition());
                    progressTracker.resetErrorCount(id);
                }

                String serializedId = idSerializer.apply(id);
                processorScanRepo.upsertScannedPosition(moduleName, serializedId, update.newScannedPosition());
                inMemoryScannedPositions.put(id, update.newScannedPosition());

                if (update.enterCatchingUp()) {
                    catchingUpSet.add(id);
                    log.debug("[{}] Processor {} entered CATCHING_UP", moduleName, id);
                }

                if (!(outcome instanceof DispatchOutcome.NoMatches)) {
                    anyDispatched = true;
                }
            }

            moduleScanCursor = windowEnd;
            moduleScanRepo.upsertScanPosition(moduleName, windowEnd);

            if (anyDispatched) {
                moduleBackoff.recordSuccess();
            }
            // neutral (fetched but nothing dispatched): no backoff change

            List<I> catchingUpSnapshot = List.copyOf(catchingUpSet);
            for (I id : catchingUpSnapshot) {
                runCatchUpIteration(id);
            }

        } catch (Exception e) {
            log.error("[{}] Error in shared cycle", moduleName, e);
        } finally {
            cycleRunning.set(false);
        }
    }

    private DispatchOutcome dispatch(I processorId, List<StoredEvent> matched, int batchSize) {
        if (matched.isEmpty()) {
            return new DispatchOutcome.NoMatches();
        }

        List<StoredEvent> toDispatch = matched.size() <= batchSize
                ? matched
                : matched.subList(0, batchSize);

        try {
            eventHandler.handle(processorId, toDispatch);
            long lastPosition = toDispatch.get(toDispatch.size() - 1).position();
            return matched.size() <= batchSize
                    ? new DispatchOutcome.Success(lastPosition)
                    : new DispatchOutcome.PartialDispatch(lastPosition);
        } catch (Exception ex) {
            C config = configs.get(processorId);
            int maxErrors = config != null ? config.getMaxErrors() : 10;
            progressTracker.recordError(processorId, ex.getMessage(), maxErrors);
            log.error("[{}] Handler failure for processor {}: {}", moduleName, processorId, ex.getMessage(), ex);
            return new DispatchOutcome.HandlerFailure();
        }
    }

    private void runCatchUpIteration(I processorId) {
        C config = configs.get(processorId);
        if (config == null || !config.isEnabled()) {
            catchingUpSet.remove(processorId);
            return;
        }
        if (progressTracker.getStatus(processorId) != ProcessorStatus.ACTIVE) {
            catchingUpSet.remove(processorId);
            return;
        }

        long scanned = inMemoryScannedPositions.getOrDefault(processorId, 0L);
        long upTo = moduleScanCursor;

        if (scanned >= upTo) {
            catchingUpSet.remove(processorId);
            return;
        }

        List<StoredEvent> events = fetchPositionOnlyBounded(scanned, upTo, config.getBatchSize());

        EventSelection selection = selections.get(processorId);
        List<StoredEvent> matched = selection != null
                ? events.stream().filter(e -> EventSelectionMatcher.matches(selection, e)).toList()
                : events;

        String serializedId = idSerializer.apply(processorId);

        if (matched.isEmpty()) {
            processorScanRepo.upsertScannedPosition(moduleName, serializedId, upTo);
            inMemoryScannedPositions.put(processorId, upTo);
            catchingUpSet.remove(processorId);
            log.debug("[{}] Processor {} caught up (sparse) to position {}", moduleName, processorId, upTo);
            return;
        }

        try {
            eventHandler.handle(processorId, matched);
            long last = matched.get(matched.size() - 1).position();
            progressTracker.updateProgress(processorId, last);
            progressTracker.resetErrorCount(processorId);
            processorScanRepo.upsertScannedPosition(moduleName, serializedId, last);
            inMemoryScannedPositions.put(processorId, last);
            if (last >= upTo) {
                catchingUpSet.remove(processorId);
                log.debug("[{}] Processor {} caught up to position {}", moduleName, processorId, upTo);
            }
        } catch (Exception ex) {
            progressTracker.recordError(processorId, ex.getMessage(), config.getMaxErrors());
            log.error("[{}] Handler failure during catch-up for processor {}", moduleName, processorId, ex);
        }
    }

    private List<StoredEvent> fetchPositionOnly(long afterPosition, int limit) {
        String sql = """
                SELECT type, tags, data, transaction_id, position, occurred_at, correlation_id, causation_id
                FROM events
                WHERE position > ?
                ORDER BY position ASC
                LIMIT ?
                """;
        try (Connection conn = readDataSource.getConnection()) {
            conn.setReadOnly(true);
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setFetchSize(limit);
                stmt.setLong(1, afterPosition);
                stmt.setInt(2, limit);
                return executeAndMap(conn, stmt);
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Fetch failed for module " + moduleName, e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Connection failed for module " + moduleName, e);
        }
    }

    private List<StoredEvent> fetchPositionOnlyBounded(long afterPosition, long upToPosition, int limit) {
        String sql = """
                SELECT type, tags, data, transaction_id, position, occurred_at, correlation_id, causation_id
                FROM events
                WHERE position > ? AND position <= ?
                ORDER BY position ASC
                LIMIT ?
                """;
        try (Connection conn = readDataSource.getConnection()) {
            conn.setReadOnly(true);
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setFetchSize(limit);
                stmt.setLong(1, afterPosition);
                stmt.setLong(2, upToPosition);
                stmt.setInt(3, limit);
                return executeAndMap(conn, stmt);
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Bounded fetch failed for module " + moduleName, e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Connection failed for module " + moduleName, e);
        }
    }

    private List<StoredEvent> executeAndMap(Connection conn, PreparedStatement stmt) throws SQLException {
        List<StoredEvent> events = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                events.add(new StoredEvent(
                        rs.getString("type"),
                        parseTagsFromArray(rs.getArray("tags")),
                        rs.getString("data").getBytes(),
                        rs.getString("transaction_id"),
                        rs.getLong("position"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getObject("correlation_id", UUID.class),
                        (Long) rs.getObject("causation_id")));
            }
        }
        conn.commit();
        return events;
    }

    private List<Tag> parseTagsFromArray(java.sql.Array array) throws SQLException {
        if (array == null) return List.of();
        String[] tagStrings = (String[]) array.getArray();
        List<Tag> tags = new ArrayList<>();
        for (String tagStr : tagStrings) {
            int equalsIndex = tagStr.indexOf('=');
            if (equalsIndex > 0) {
                tags.add(new Tag(tagStr.substring(0, equalsIndex), tagStr.substring(equalsIndex + 1)));
            }
        }
        return tags;
    }

    private void requestImmediatePoll() {
        if (shuttingDown) return;
        if (!cycleRunning.get()) {
            taskScheduler.schedule(this::runSharedCycle, Instant.now());
        }
    }

    @Override
    public int process(I processorId) {
        runSharedCycle();
        return 0;
    }

    @Override
    public void start() {
        initializeSchedulersIfNeeded("manual start()");
    }

    @Override
    @PreDestroy
    public void stop() {
        synchronized (lifecycleMonitor) {
            shuttingDown = true;
            if (sharedSchedule != null) {
                sharedSchedule.cancel(false);
                sharedSchedule = null;
            }
            if (leaderRetrySchedule != null) {
                leaderRetrySchedule.cancel(false);
                leaderRetrySchedule = null;
            }
            wakeupSource.close();
            leaderElector.releaseGlobalLeader();
            schedulersInitialized = false;
            log.info("[{}] Shared-fetch processor stopped", moduleName);
        }
    }

    @Override
    public void pause(I processorId) {
        progressTracker.setStatus(processorId, ProcessorStatus.PAUSED);
    }

    @Override
    public void resume(I processorId) {
        progressTracker.setStatus(processorId, ProcessorStatus.ACTIVE);
        long scanned = inMemoryScannedPositions.getOrDefault(processorId, 0L);
        if (scanned < moduleScanCursor) {
            catchingUpSet.add(processorId);
        }
    }

    @Override
    public ProcessorStatus getStatus(I processorId) {
        return progressTracker.getStatus(processorId);
    }

    @Override
    public Map<I, ProcessorStatus> getAllStatuses() {
        Map<I, ProcessorStatus> statuses = new ConcurrentHashMap<>();
        for (I id : configs.keySet()) {
            statuses.put(id, progressTracker.getStatus(id));
        }
        return statuses;
    }

    @Override
    public BackoffState getBackoffStateForProcessor(I processorId) {
        return moduleBackoff;
    }

    @Override
    public Map<I, BackoffState> getAllBackoffStates() {
        Map<I, BackoffState> result = new HashMap<>();
        for (I id : configs.keySet()) {
            result.put(id, moduleBackoff);
        }
        return result;
    }
}
