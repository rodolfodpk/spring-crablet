package com.crablet.eventpoller.internal;

import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.metrics.BackoffStateMetric;
import com.crablet.eventpoller.metrics.ProcessingCycleMetric;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSource;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSource;
import com.crablet.eventstore.StoredEvent;
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
import java.io.EOFException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Generic implementation of EventProcessor.
 * Handles scheduling, leader election, backoff, and event processing.
 */
public class EventProcessorImpl<T extends ProcessorConfig<I>, I> implements EventProcessor<T, I> {

    private static final Logger log = LoggerFactory.getLogger(EventProcessorImpl.class);

    private final Map<I, T> configs;
    private final LeaderElector leaderElector;
    private final ProgressTracker<I> progressTracker;
    private final EventFetcher<I> eventFetcher;
    private final EventHandler<I> eventHandler;
    private final DataSource writeDataSource;
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher eventPublisher;
    private final ProcessorWakeupSource wakeupSource;
    private final Object lifecycleMonitor = new Object();

    // Track active schedulers
    private final Map<I, ScheduledFuture<?>> activeSchedulers = new ConcurrentHashMap<>();

    // Track backoff states
    private final Map<I, BackoffState> backoffStates = new ConcurrentHashMap<>();
    private final Map<I, Boolean> runningProcessors = new ConcurrentHashMap<>();
    private final Map<I, Boolean> immediateRunRequested = new ConcurrentHashMap<>();

    // Leader retry scheduler
    private ScheduledFuture<?> leaderRetryScheduler;

    // Cooldown for leader retry
    private static final long LEADER_RETRY_COOLDOWN_MS = 5000;
    private volatile long lastLeaderRetryTimestamp = 0;

    // Shutdown flag to prevent processing during shutdown
    private volatile boolean shuttingDown = false;

    public EventProcessorImpl(
            Map<I, T> configs,
            LeaderElector leaderElector,
            ProgressTracker<I> progressTracker,
            EventFetcher<I> eventFetcher,
            EventHandler<I> eventHandler,
            DataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {
        this(configs, leaderElector, progressTracker, eventFetcher, eventHandler, writeDataSource, taskScheduler, eventPublisher, new NoopProcessorWakeupSource());
    }

    public EventProcessorImpl(
            Map<I, T> configs,
            LeaderElector leaderElector,
            ProgressTracker<I> progressTracker,
            EventFetcher<I> eventFetcher,
            EventHandler<I> eventHandler,
            DataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            ProcessorWakeupSource wakeupSource) {
        this.configs = configs;
        this.leaderElector = leaderElector;
        this.progressTracker = progressTracker;
        this.eventFetcher = eventFetcher;
        this.eventHandler = eventHandler;
        this.writeDataSource = writeDataSource;
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
        this.wakeupSource = wakeupSource;
    }

    // Track if schedulers have been initialized
    private volatile boolean schedulersInitialized = false;

    /**
     * @PostConstruct method - logs when bean is created but doesn't start schedulers yet.
     * Schedulers are initialized via ContextRefreshedEvent to ensure all beans (including Flyway) are ready.
     */
    @PostConstruct
    public void initializeSchedulers() {
        log.debug("[EventProcessorImpl] Bean created at {}. Waiting for Spring lifecycle event before starting schedulers.", Instant.now());
    }

    /**
     * Initialize schedulers when Spring context is fully refreshed.
     * ContextRefreshedEvent fires after all beans are initialized, including Flyway.
     * This is more reliable than ApplicationReadyEvent in tests.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void initializeSchedulersOnContextRefresh(ContextRefreshedEvent event) {
        log.debug("[EventProcessorImpl] ContextRefreshedEvent received at {}.", Instant.now());
        initializeSchedulersIfNeeded("ContextRefreshedEvent");
    }

    /**
     * Also listen for ApplicationReadyEvent as a fallback (fires later, after application is fully ready).
     * This ensures schedulers start even if ContextRefreshedEvent doesn't fire in some scenarios.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeSchedulersOnReady(ApplicationReadyEvent event) {
        log.debug("[EventProcessorImpl] ApplicationReadyEvent received at {}.", Instant.now());
        initializeSchedulersIfNeeded("ApplicationReadyEvent");
    }

    private void initializeSchedulersIfNeeded(String trigger) {
        synchronized (lifecycleMonitor) {
            if (schedulersInitialized) {
                log.debug("[EventProcessorImpl] Schedulers already initialized, skipping {}", trigger);
                return;
            }

            boolean anyEnabled = configs.values().stream()
                .anyMatch(ProcessorConfig::isEnabled);

            if (!anyEnabled) {
                log.info("[EventProcessorImpl] No processors enabled, skipping scheduler initialization");
                schedulersInitialized = true;
                return;
            }

            try {
                shuttingDown = false;
                log.info("Starting event processor schedulers via {}", trigger);
                doInitializeSchedulers();
                wakeupSource.start(this::requestImmediatePoll);
                schedulersInitialized = true;
                log.info("Event processor schedulers started");
            } catch (Exception e) {
                log.error("Failed to initialize event processor schedulers via {}", trigger, e);
            }
        }
    }

    private void doInitializeSchedulers() {
        // Try to acquire global leader lock on startup
        leaderElector.tryAcquireGlobalLeader();

        // Register dedicated scheduler for leader election retry
        long leaderRetryInterval = configs.values().stream()
            .filter(ProcessorConfig::isEnabled)
            .findFirst()
            .map(ProcessorConfig::getLeaderElectionRetryIntervalMs)
            .orElse(30000L);
        leaderRetryScheduler = taskScheduler.scheduleAtFixedRate(
            this::leaderRetryTask,
            Duration.ofMillis(leaderRetryInterval)
        );
        log.debug("[EventProcessorImpl] Registered leader election retry scheduler with interval {}ms", leaderRetryInterval);

        // Register one scheduler per processor
        int enabledCount = 0;
        for (var entry : configs.entrySet()) {
            I processorId = entry.getKey();
            T config = entry.getValue();

            if (!config.isEnabled()) {
                log.debug("[EventProcessorImpl] Processor {} is disabled, skipping", processorId);
                continue;
            }

            enabledCount++;
            long pollingInterval = config.getPollingIntervalMs();

            log.debug("[EventProcessorImpl] Registering scheduler for processor {} with polling interval {}ms", processorId, pollingInterval);

            // Create backoff state if enabled
            if (config.isBackoffEnabled()) {
                BackoffState backoffState = new BackoffState(
                    config.getBackoffThreshold(),
                    config.getBackoffMultiplier(),
                    pollingInterval,
                    config.getBackoffMaxSeconds()
                );
                backoffStates.put(processorId, backoffState);
                log.debug("[EventProcessorImpl] Created backoff state for processor {}", processorId);
            }

            long initialDelayMs = 500; // 500ms initial delay - Flyway should be done by now via ContextRefreshedEvent
            scheduleProcessorRun(processorId, initialDelayMs);
            log.debug("[EventProcessorImpl] Registered scheduler for processor {} with initial delay {}ms",
                    processorId, initialDelayMs);
        }

        log.debug("[EventProcessorImpl] Scheduler initialization complete. Registered {} enabled processors.", enabledCount);
    }

    @PreDestroy
    public void shutdownSchedulers() {
        synchronized (lifecycleMonitor) {
            shuttingDown = true;
            boolean hadActiveWork = !activeSchedulers.isEmpty() || leaderRetryScheduler != null || schedulersInitialized;
            if (hadActiveWork) {
                log.info("Stopping event processor schedulers");
            } else {
                log.debug("[EventProcessorImpl] shutdownSchedulers called with no active schedulers");
            }
            activeSchedulers.values().forEach(future -> {
                if (future != null) {
                    future.cancel(false);
                }
            });
            activeSchedulers.clear();
            backoffStates.clear();
            runningProcessors.clear();
            immediateRunRequested.clear();

            // Cancel leader retry scheduler
            if (leaderRetryScheduler != null) {
                leaderRetryScheduler.cancel(false);
                leaderRetryScheduler = null;
            }

            wakeupSource.close();

            // Allow a future manual start() to recreate the schedulers.
            schedulersInitialized = false;
            lastLeaderRetryTimestamp = 0;

            // Release global leader lock
            leaderElector.releaseGlobalLeader();

            if (hadActiveWork) {
                log.info("Event processor schedulers stopped");
            }
        }
    }

    /**
     * Checks if an exception is a connection error that occurs during database shutdown.
     * These are expected during test shutdown when Testcontainers stops the database.
     */
    private boolean isShutdownConnectionError(Exception e) {
        // Check for EOFException (connection closed)
        if (e.getCause() instanceof EOFException) {
            return true;
        }

        // Check for PostgreSQL-specific shutdown errors
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("i/o error") ||
                   lowerMessage.contains("connection has been closed") ||
                   lowerMessage.contains("terminating connection") ||
                   lowerMessage.contains("unexpected postmaster exit") ||
                   lowerMessage.contains("this connection has been closed");
        }

        // Check for SQLState codes that indicate connection shutdown
        if (e instanceof java.sql.SQLException) {
            String sqlState = ((java.sql.SQLException) e).getSQLState();
            // 57P01 = terminating connection due to administrator command
            // 08006 = connection failure
            if ("57P01".equals(sqlState) || "08006".equals(sqlState)) {
                return true;
            }
        }

        return false;
    }

    private void leaderRetryTask() {
        // Skip if shutting down
        if (shuttingDown) {
            return;
        }

        // If not leader, attempt to acquire lock
        if (!leaderElector.isGlobalLeader()) {
            boolean acquired = leaderElector.tryAcquireGlobalLeader();
            if (acquired) {
                log.info("Became leader after retry - starting to process");
            }
        }
    }

    private void scheduledTask(I processorId) {
        log.trace("[EventProcessorImpl] scheduledTask() called for processor: {} at {}", processorId, Instant.now());
        long nextDelayMs = 0L;
        String instanceId = leaderElector.getInstanceId();
        boolean acquiredRunSlot = false;

        try {
            // Skip processing if shutting down
            if (shuttingDown) {
                log.trace("[EventProcessorImpl] Shutting down, skipping scheduled task for {}", processorId);
                return;
            }

            T config = configs.get(processorId);
            if (config == null || !config.isEnabled()) {
                return;
            }
            nextDelayMs = config.getPollingIntervalMs();

            // If not leader, retry lock acquisition with cooldown
            if (!leaderElector.isGlobalLeader()) {
                long now = System.currentTimeMillis();
                if (now - lastLeaderRetryTimestamp >= LEADER_RETRY_COOLDOWN_MS) {
                    lastLeaderRetryTimestamp = now;
                    boolean acquired = leaderElector.tryAcquireGlobalLeader();
                    if (acquired) {
                        log.info("Became leader after retry in scheduledTask - starting to process");
                    }
                }
                // Still return if not leader
                if (!leaderElector.isGlobalLeader()) {
                    return;
                }
            }

            BackoffState backoffState = backoffStates.get(processorId);
            acquiredRunSlot = runningProcessors.putIfAbsent(processorId, Boolean.TRUE) == null;
            if (!acquiredRunSlot) {
                log.trace("Processor {} is already running, skipping duplicate scheduled task", processorId);
                return;
            }
            log.trace("[EventProcessorImpl] Calling process() for processor: {} at {}", processorId, Instant.now());
            int processed = process(processorId);
            log.trace("[EventProcessorImpl] process() completed for processor: {}, processed: {} events at {}",
                     processorId, processed, Instant.now());
            eventPublisher.publishEvent(new ProcessingCycleMetric(processorId.toString(), instanceId, processed, processed == 0));

            // Update backoff state
            if (backoffState != null) {
                if (processed > 0) {
                    backoffState.recordSuccess();
                    eventPublisher.publishEvent(new BackoffStateMetric(processorId.toString(), instanceId, false, 0));
                    log.debug("Processed {} events for {} - backoff reset", processed, processorId);
                } else {
                    backoffState.recordEmpty();
                    eventPublisher.publishEvent(new BackoffStateMetric(processorId.toString(), instanceId, backoffState.getEmptyPollCount() > 0, backoffState.getEmptyPollCount()));
                    nextDelayMs = backoffState.getNextDelayMs();
                    log.trace("Scheduling next poll for {} in {}ms after {} consecutive empty polls",
                        processorId, nextDelayMs, backoffState.getEmptyPollCount());
                }
            } else if (processed > 0) {
                log.debug("Processed {} events for {}", processed, processorId);
            }
        } catch (Exception e) {
            // During shutdown, connection errors are expected - suppress or log at trace level
            if (isShutdownConnectionError(e)) {
                if (shuttingDown) {
                    log.trace("Connection error during shutdown for {} (expected): {}", processorId, e.getMessage());
                } else {
                    // Database might be shutting down (e.g., in tests) even if we haven't set shuttingDown yet
                    log.debug("Connection error during database shutdown for {} (expected): {}", processorId, e.getMessage());
                }
            } else {
                log.error("Error in scheduler for {}", processorId, e);
            }
            eventPublisher.publishEvent(new ProcessingCycleMetric(processorId.toString(), instanceId, 0, true));
            // Don't update backoff on errors - let it retry normally
        } finally {
            if (acquiredRunSlot) {
                runningProcessors.remove(processorId);
                if (immediateRunRequested.remove(processorId) != null) {
                    nextDelayMs = 0L;
                }
                scheduleProcessorRun(processorId, nextDelayMs);
            }
        }
    }

    private void requestImmediatePoll() {
        if (shuttingDown) {
            return;
        }

        for (var entry : configs.entrySet()) {
            I processorId = entry.getKey();
            T config = entry.getValue();
            if (!config.isEnabled()) {
                continue;
            }

            immediateRunRequested.put(processorId, Boolean.TRUE);
            ScheduledFuture<?> existing = activeSchedulers.get(processorId);
            if (existing != null) {
                existing.cancel(false);
            }
            scheduleProcessorRun(processorId, 0L);
        }
    }

    private void scheduleProcessorRun(I processorId, long delayMs) {
        if (shuttingDown) {
            return;
        }

        T config = configs.get(processorId);
        if (config == null || !config.isEnabled()) {
            activeSchedulers.remove(processorId);
            return;
        }

        long sanitizedDelayMs = Math.max(delayMs, 0L);
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> scheduledTask(processorId),
            Instant.now().plusMillis(sanitizedDelayMs)
        );
        activeSchedulers.put(processorId, future);
        log.trace("[EventProcessorImpl] Scheduled next run for {} in {}ms", processorId, sanitizedDelayMs);
    }

    @Override
    public int process(I processorId) {
        // Note: Manual process() calls should work even during shutdown
        // The shuttingDown flag only prevents scheduled tasks from running

        T config = configs.get(processorId);
        if (config == null) {
            throw new IllegalArgumentException("Processor not found: " + processorId);
        }

        // Check status
        ProcessorStatus status = progressTracker.getStatus(processorId);
        if (status == ProcessorStatus.PAUSED || status == ProcessorStatus.FAILED) {
            log.trace("Processor {} is {} (status: {}), skipping", processorId, status, status);
            return 0;
        }

        // Auto-register if needed
        log.trace("[EventProcessorImpl] About to call autoRegister() for processor: {} at {}", processorId, Instant.now());
        try {
            progressTracker.autoRegister(processorId, leaderElector.getInstanceId());
            log.trace("[EventProcessorImpl] autoRegister() completed for processor: {} at {}", processorId, Instant.now());
        } catch (RuntimeException e) {
            // If auto-register fails due to missing tables (Flyway not ready), log and continue
            // The table will be created by Flyway, and auto-register will succeed on next call
            if (e.getMessage() != null && e.getMessage().contains("relation") &&
                e.getMessage().contains("does not exist")) {
                log.debug("[EventProcessorImpl] Auto-register failed for {} - table not ready yet (Flyway may not have run): {}. " +
                         "Will retry on next cycle.", processorId, e.getMessage());
                // Don't throw - allow processing to continue, table will be ready soon
                return 0; // Return 0 processed events for this cycle
            }
            throw e; // Re-throw other exceptions
        }

        // Get last position
        long lastPosition = progressTracker.getLastPosition(processorId);

        // Fetch events (uses read replica via EventFetcher)
        List<StoredEvent> events = eventFetcher.fetchEvents(processorId, lastPosition, config.getBatchSize());

        if (events.isEmpty()) {
            return 0;
        }

        try {
            int handled = eventHandler.handle(processorId, events);

            // Update progress (uses write DataSource via ProgressTracker)
            long newPosition = events.get(events.size() - 1).position();
            progressTracker.updateProgress(processorId, newPosition);
            progressTracker.resetErrorCount(processorId);

            return handled;
        } catch (Exception e) {
            progressTracker.recordError(processorId, e.getMessage(), config.getMaxErrors());
            throw new RuntimeException("Failed to handle events for processor: " + processorId, e);
        }
    }

    @Override
    public void start() {
        initializeSchedulersIfNeeded("manual start()");
    }

    @Override
    public void stop() {
        shutdownSchedulers();
    }

    @Override
    public void pause(I processorId) {
        progressTracker.setStatus(processorId, ProcessorStatus.PAUSED);
    }

    @Override
    public void resume(I processorId) {
        progressTracker.setStatus(processorId, ProcessorStatus.ACTIVE);
    }

    @Override
    public ProcessorStatus getStatus(I processorId) {
        return progressTracker.getStatus(processorId);
    }

    @Override
    public Map<I, ProcessorStatus> getAllStatuses() {
        Map<I, ProcessorStatus> statuses = new ConcurrentHashMap<>();
        for (I processorId : configs.keySet()) {
            statuses.put(processorId, progressTracker.getStatus(processorId));
        }
        return statuses;
    }

    /**
     * Get backoff state for a specific processor (for management/monitoring).
     */
    public BackoffState getBackoffState(I processorId) {
        return backoffStates.get(processorId);
    }

    /**
     * Get all backoff states (for management/monitoring).
     */
    public Map<I, BackoffState> getAllBackoffStates() {
        return new ConcurrentHashMap<>(backoffStates);
    }
}
