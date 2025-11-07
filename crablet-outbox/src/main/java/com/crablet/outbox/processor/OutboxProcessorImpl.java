package com.crablet.outbox.processor;

import com.crablet.outbox.OutboxProcessor;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.leader.OutboxLeaderElector;
import com.crablet.outbox.metrics.ProcessingCycleMetric;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.TopicConfig;
import org.springframework.context.ApplicationEventPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Core implementation of the Outbox Pattern for reliable event publishing.
 * Uses one independent scheduler per (topic, publisher) for better isolation.
 * Users must define as @Bean in their Spring configuration.
 */
public class OutboxProcessorImpl implements OutboxProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorImpl.class);
    
    // Core dependencies
    private final OutboxConfig config;
    private final OutboxLeaderElector leaderElector;
    private final OutboxPublishingService publishingService;
    
    // Supporting infrastructure
    private final TopicConfigurationProperties topicConfigProperties;
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher eventPublisher;
    
    // Track which publishers are available by name
    private final Map<String, OutboxPublisher> publisherByName = new ConcurrentHashMap<>();
    
    // Track active schedulers
    private final Map<String, ScheduledFuture<?>> activeSchedulers = new ConcurrentHashMap<>();
    
    // Track backoff states for each (topic, publisher) pair
    private final Map<String, BackoffState> backoffStates = new ConcurrentHashMap<>();
    
    // Dedicated scheduler for leader election retry
    private ScheduledFuture<?> leaderRetryScheduler;
    
    // Cooldown mechanism for leader retry in scheduledTask
    private static final long LEADER_RETRY_COOLDOWN_MS = 5000; // 5 seconds cooldown between retries
    private volatile long lastLeaderRetryTimestamp = 0;
    
    /**
     * Creates a new OutboxProcessorImpl.
     *
     * @param config outbox configuration
     * @param jdbcTemplate JDBC template for database operations
     * @param readDataSource data source for read operations
     * @param publishers list of publisher implementations
     * @param leaderElector leader election component
     * @param publishingService publishing service
     * @param circuitBreakerRegistry circuit breaker registry for resilience
     * @param globalStatistics global statistics publisher
     * @param topicConfigProperties topic configuration properties
     * @param taskScheduler task scheduler for periodic processing
     * @param eventPublisher event publisher for metrics (required).
     *                       Spring Boot automatically provides an ApplicationEventPublisher bean.
     *                       See crablet-metrics-micrometer for automatic metrics collection.
     */
    public OutboxProcessorImpl(
            OutboxConfig config,
            JdbcTemplate jdbcTemplate,
            DataSource readDataSource,
            List<OutboxPublisher> publishers,
            OutboxLeaderElector leaderElector,
            OutboxPublishingService publishingService,
            CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics,
            TopicConfigurationProperties topicConfigProperties,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        if (readDataSource == null) {
            throw new IllegalArgumentException("readDataSource must not be null");
        }
        if (publishers == null) {
            throw new IllegalArgumentException("publishers must not be null");
        }
        if (leaderElector == null) {
            throw new IllegalArgumentException("leaderElector must not be null");
        }
        if (publishingService == null) {
            throw new IllegalArgumentException("publishingService must not be null");
        }
        if (circuitBreakerRegistry == null) {
            throw new IllegalArgumentException("circuitBreakerRegistry must not be null");
        }
        if (globalStatistics == null) {
            throw new IllegalArgumentException("globalStatistics must not be null");
        }
        if (topicConfigProperties == null) {
            throw new IllegalArgumentException("topicConfigProperties must not be null");
        }
        if (taskScheduler == null) {
            throw new IllegalArgumentException("taskScheduler must not be null");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.config = config;
        this.leaderElector = leaderElector;
        this.publishingService = publishingService;
        this.topicConfigProperties = topicConfigProperties;
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
        
        // Build publisher lookup map
        for (OutboxPublisher publisher : publishers) {
            publisherByName.put(publisher.getName(), publisher);
        }
        
        log.info("Outbox processor initialized with {} publishers", publishers.size());
        publishers.forEach(p -> log.info("  - {}", p.getName()));
    }
    
    /**
     * Initialize per-publisher schedulers on startup.
     */
    @PostConstruct
    public void initializeSchedulers() {
        if (!config.isEnabled()) {
            log.info("Outbox processing is disabled");
            return;
        }
        
        // Try to acquire global leader lock on startup
        leaderElector.tryAcquireGlobalLeader();
        
        // Register dedicated scheduler for leader election retry (for followers)
        long leaderRetryInterval = config.getLeaderElectionRetryIntervalMs();
        leaderRetryScheduler = taskScheduler.scheduleAtFixedRate(
            this::leaderRetryTask,
            Duration.ofMillis(leaderRetryInterval)
        );
        log.info("Registered leader election retry scheduler with interval {}ms", leaderRetryInterval);
        
        // Register one scheduler per (topic, publisher)
        Map<String, TopicConfig> topicConfigs = config.getTopics();
        for (var topicEntry : topicConfigs.entrySet()) {
            String topicName = topicEntry.getKey();
            TopicConfig topicConfig = topicEntry.getValue();
            
            for (String publisherName : topicConfig.getPublishers()) {
                long pollingInterval = getPollingIntervalForPublisher(topicName, publisherName);
                String schedulerKey = topicName + ":" + publisherName;
                
                // Create backoff state if enabled
                if (config.isBackoffEnabled()) {
                    BackoffState backoffState = new BackoffState(
                        config.getBackoffThreshold(),
                        config.getBackoffMultiplier(),
                        pollingInterval,
                        config.getBackoffMaxSeconds()
                    );
                    backoffStates.put(schedulerKey, backoffState);
                }
                
                ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                    () -> scheduledTask(topicName, publisherName),
                    Duration.ofMillis(pollingInterval)
                );
                
                activeSchedulers.put(schedulerKey, future);
                log.info("Registered scheduler for ({}, {}) with interval {}ms", 
                    topicName, publisherName, pollingInterval);
            }
        }
    }
    
    /**
     * Shutdown schedulers on destroy.
     */
    @PreDestroy
    public void shutdownSchedulers() {
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
     * Dedicated task for leader election retry.
     * Runs periodically to allow followers to detect leader crashes.
     */
    private void leaderRetryTask() {
        if (!config.isEnabled()) {
            return;
        }
        
        // If not leader, attempt to acquire lock
        if (!leaderElector.isGlobalLeader()) {
            boolean acquired = leaderElector.tryAcquireGlobalLeader();
            if (acquired) {
                log.info("Became leader after retry - starting to process publishers");
            }
        }
    }
    
    /**
     * Scheduled task for a specific (topic, publisher) pair.
     */
    private void scheduledTask(String topicName, String publisherName) {
        if (!config.isEnabled()) {
            return;
        }
        
        // If not leader, retry lock acquisition with cooldown to prevent redundant retries
        if (!leaderElector.isGlobalLeader()) {
            long now = System.currentTimeMillis();
            if (now - lastLeaderRetryTimestamp >= LEADER_RETRY_COOLDOWN_MS) {
                lastLeaderRetryTimestamp = now;
                boolean acquired = leaderElector.tryAcquireGlobalLeader();
                if (acquired) {
                    log.info("Became leader after retry in scheduledTask - starting to process publishers");
                }
            }
            // Still return if not leader (either failed to acquire or cooldown active)
            if (!leaderElector.isGlobalLeader()) {
                return;
            }
        }
        
        String key = topicName + ":" + publisherName;
        BackoffState backoffState = backoffStates.get(key);
        
        // Check if we should skip this cycle
        if (backoffState != null && backoffState.shouldSkip()) {
            log.trace("Skipping poll for ({}, {}) due to backoff (empty polls: {})", 
                topicName, publisherName, backoffState.getEmptyPollCount());
            return;
        }
        
        try {
            int published = publishingService.publishForTopicPublisher(topicName, publisherName);
            eventPublisher.publishEvent(new ProcessingCycleMetric());
            
            // Update backoff state
            if (backoffState != null) {
                if (published > 0) {
                    backoffState.recordSuccess();
                    log.debug("Published {} events for ({}, {}) - backoff reset", 
                        published, topicName, publisherName);
                } else {
                    backoffState.recordEmpty();
                }
            } else if (published > 0) {
                log.debug("Published {} events for ({}, {})", published, topicName, publisherName);
            }
        } catch (Exception e) {
            log.error("Error in scheduler for ({}, {})", topicName, publisherName, e);
            eventPublisher.publishEvent(new ProcessingCycleMetric());
            // Don't update backoff on errors - let it retry normally
        }
    }
    
    /**
     * Get polling interval for a specific publisher, falling back to global default.
     */
    private long getPollingIntervalForPublisher(String topicName, String publisherName) {
        TopicConfigurationProperties.TopicProperties topicProps = 
            topicConfigProperties.getTopics().get(topicName);
        
        if (topicProps != null && topicProps.getPublisherConfigs() != null) {
            for (TopicConfigurationProperties.PublisherProperties pubConfig : topicProps.getPublisherConfigs()) {
                if (publisherName.equals(pubConfig.getName()) && pubConfig.getPollingIntervalMs() != null) {
                    return pubConfig.getPollingIntervalMs();
                }
            }
        }
        
        return config.getPollingIntervalMs(); // Global fallback
    }
    
    @Override
    public int processPending() {
        // Process all configured (topic, publisher) pairs
        // Used for synchronous testing
        int totalProcessed = 0;
        Map<String, TopicConfig> topicConfigs = config.getTopics();
        
        for (var topicEntry : topicConfigs.entrySet()) {
            String topicName = topicEntry.getKey();
            TopicConfig topicConfig = topicEntry.getValue();
            
            for (String publisherName : topicConfig.getPublishers()) {
                try {
                    int published = publishingService.publishForTopicPublisher(topicName, publisherName);
                    totalProcessed += published;
                } catch (Exception e) {
                    log.error("Error processing publisher {} for topic {}: {}", 
                        publisherName, topicName, e.getMessage());
                }
            }
        }
        
        return totalProcessed;
    }
    
    @Override
    public void markPublished(long outboxId) {
        // No-op: publisher-offset-tracking updates last_position directly
    }
    
    @Override
    public void markFailed(long outboxId, String error) {
        // No-op: publisher-offset-tracking updates error_count directly
    }
    
    /**
     * Get backoff state for a specific publisher (for management/monitoring).
     */
    public BackoffState getBackoffState(String topic, String publisher) {
        String key = topic + ":" + publisher;
        return backoffStates.get(key);
    }
    
    /**
     * Get all backoff states (for management/monitoring).
     */
    public Map<String, BackoffState> getAllBackoffStates() {
        return new ConcurrentHashMap<>(backoffStates);
    }
}
