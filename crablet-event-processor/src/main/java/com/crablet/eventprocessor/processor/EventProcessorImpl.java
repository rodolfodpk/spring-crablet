package com.crablet.eventprocessor.processor;

import com.crablet.eventprocessor.backoff.BackoffState;
import com.crablet.eventprocessor.leader.LeaderElector;
import com.crablet.eventprocessor.progress.ProgressTracker;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventprocessor.metrics.ProcessingCycleMetric;
import com.crablet.eventstore.store.StoredEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.time.Duration;
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
    private final DataSource writeDataSource;  // For handlers that need DB writes (e.g., views)
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher eventPublisher;
    
    // Track active schedulers
    private final Map<I, ScheduledFuture<?>> activeSchedulers = new ConcurrentHashMap<>();
    
    // Track backoff states
    private final Map<I, BackoffState> backoffStates = new ConcurrentHashMap<>();
    
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
        this.configs = configs;
        this.leaderElector = leaderElector;
        this.progressTracker = progressTracker;
        this.eventFetcher = eventFetcher;
        this.eventHandler = eventHandler;
        this.writeDataSource = writeDataSource;
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
    }
    
    @PostConstruct
    public void initializeSchedulers() {
        // Check if any processor is enabled
        boolean anyEnabled = configs.values().stream()
            .anyMatch(ProcessorConfig::isEnabled);
        
        if (!anyEnabled) {
            log.info("No processors enabled, skipping scheduler initialization");
            return;
        }
        
        // Try to acquire global leader lock on startup
        leaderElector.tryAcquireGlobalLeader();
        
        // Register dedicated scheduler for leader election retry
        long leaderRetryInterval = 30000; // Default, could be configurable
        leaderRetryScheduler = taskScheduler.scheduleAtFixedRate(
            this::leaderRetryTask,
            Duration.ofMillis(leaderRetryInterval)
        );
        log.info("Registered leader election retry scheduler with interval {}ms", leaderRetryInterval);
        
        // Register one scheduler per processor
        for (var entry : configs.entrySet()) {
            I processorId = entry.getKey();
            T config = entry.getValue();
            
            if (!config.isEnabled()) {
                continue;
            }
            
            long pollingInterval = config.getPollingIntervalMs();
            
            // Create backoff state if enabled
            if (config.isBackoffEnabled()) {
                BackoffState backoffState = new BackoffState(
                    config.getBackoffThreshold(),
                    config.getBackoffMultiplier(),
                    pollingInterval,
                    config.getBackoffMaxSeconds()
                );
                backoffStates.put(processorId, backoffState);
            }
            
            ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> scheduledTask(processorId),
                Duration.ofMillis(pollingInterval)
            );
            
            activeSchedulers.put(processorId, future);
            log.info("Registered scheduler for processor {} with interval {}ms", processorId, pollingInterval);
        }
    }
    
    @PreDestroy
    public void shutdownSchedulers() {
        shuttingDown = true;
        log.info("Shutting down {} active schedulers", activeSchedulers.size());
        activeSchedulers.values().forEach(future -> {
            if (future != null) {
                future.cancel(false);
            }
        });
        activeSchedulers.clear();
        backoffStates.clear();
        
        // Cancel leader retry scheduler
        if (leaderRetryScheduler != null) {
            leaderRetryScheduler.cancel(false);
            leaderRetryScheduler = null;
        }
        
        // Release global leader lock
        leaderElector.releaseGlobalLeader();
    }
    
    /**
     * Checks if an exception is a connection error that occurs during database shutdown.
     * These are expected during test shutdown when Testcontainers stops the database.
     */
    private boolean isShutdownConnectionError(Exception e) {
        // Check for EOFException (connection closed)
        if (e.getCause() instanceof java.io.EOFException) {
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
        // Skip processing if shutting down
        if (shuttingDown) {
            log.trace("Shutting down, skipping scheduled task for {}", processorId);
            return;
        }
        
        T config = configs.get(processorId);
        if (config == null || !config.isEnabled()) {
            return;
        }
        
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
        
        // Check if we should skip this cycle
        if (backoffState != null && backoffState.shouldSkip()) {
            log.trace("Skipping poll for {} due to backoff (empty polls: {})", 
                processorId, backoffState.getEmptyPollCount());
            return;
        }
        
        try {
            int processed = process(processorId);
            eventPublisher.publishEvent(new ProcessingCycleMetric());
            
            // Update backoff state
            if (backoffState != null) {
                if (processed > 0) {
                    backoffState.recordSuccess();
                    log.debug("Processed {} events for {} - backoff reset", processed, processorId);
                } else {
                    backoffState.recordEmpty();
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
            eventPublisher.publishEvent(new ProcessingCycleMetric());
            // Don't update backoff on errors - let it retry normally
        }
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
        progressTracker.autoRegister(processorId, leaderElector.getInstanceId());
        
        // Get last position
        long lastPosition = progressTracker.getLastPosition(processorId);
        
        // Fetch events (uses read replica via EventFetcher)
        List<StoredEvent> events = eventFetcher.fetchEvents(processorId, lastPosition, config.getBatchSize());
        
        if (events.isEmpty()) {
            return 0;
        }
        
        // Handle events - pass write DataSource for handlers that need it (e.g., views)
        try {
            int handled = eventHandler.handle(processorId, events, writeDataSource);
            
            // Update progress (uses write DataSource via ProgressTracker)
            long newPosition = events.get(events.size() - 1).position();
            progressTracker.updateProgress(processorId, newPosition);
            progressTracker.resetErrorCount(processorId);
            
            return handled;
        } catch (Exception e) {
            progressTracker.recordError(processorId, e.getMessage(), 10); // Default max errors
            throw new RuntimeException("Failed to handle events for processor: " + processorId, e);
        }
    }
    
    @Override
    public void start() {
        // Reset shuttingDown flag to allow schedulers to run
        shuttingDown = false;
        // Already started via @PostConstruct, but can be called to restart
        initializeSchedulers();
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

