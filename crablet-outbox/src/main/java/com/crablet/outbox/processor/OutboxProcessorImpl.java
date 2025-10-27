package com.crablet.outbox.processor;

import com.crablet.outbox.OutboxProcessor;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.leader.OutboxLeaderElector;
import com.crablet.outbox.metrics.OutboxMetrics;
import com.crablet.outbox.metrics.OutboxPublisherMetrics;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.TopicConfig;
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
import java.util.Set;
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
    private final OutboxMetrics outboxMetrics;
    private final TopicConfigurationProperties topicConfigProperties;
    private final TaskScheduler taskScheduler;
    
    // Track which publishers are available by name
    private final Map<String, OutboxPublisher> publisherByName = new ConcurrentHashMap<>();
    
    // Track active schedulers
    private final Map<String, ScheduledFuture<?>> activeSchedulers = new ConcurrentHashMap<>();
    
    public OutboxProcessorImpl(
            OutboxConfig config,
            JdbcTemplate jdbcTemplate,
            DataSource readDataSource,
            List<OutboxPublisher> publishers,
            OutboxLeaderElector leaderElector,
            OutboxPublishingService publishingService,
            OutboxMetrics outboxMetrics,
            OutboxPublisherMetrics publisherMetrics,
            CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics,
            TopicConfigurationProperties topicConfigProperties,
            TaskScheduler taskScheduler) {
        this.config = config;
        this.leaderElector = leaderElector;
        this.publishingService = publishingService;
        this.outboxMetrics = outboxMetrics;
        this.topicConfigProperties = topicConfigProperties;
        this.taskScheduler = taskScheduler;
        
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
        
        // Try to acquire global leader lock
            leaderElector.tryAcquireGlobalLeader();
        
        // Register one scheduler per (topic, publisher)
        Map<String, TopicConfig> topicConfigs = config.getTopics();
        for (var topicEntry : topicConfigs.entrySet()) {
            String topicName = topicEntry.getKey();
            TopicConfig topicConfig = topicEntry.getValue();
            
            for (String publisherName : topicConfig.getPublishers()) {
                long pollingInterval = getPollingIntervalForPublisher(topicName, publisherName);
                
                ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                    () -> scheduledTask(topicName, publisherName),
                    Duration.ofMillis(pollingInterval)
                );
                
                String schedulerKey = topicName + ":" + publisherName;
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
        
        // Release global leader lock
        leaderElector.releaseGlobalLeader();
    }
    
    /**
     * Scheduled task for a specific (topic, publisher) pair.
     */
    private void scheduledTask(String topicName, String publisherName) {
        if (!config.isEnabled() || !leaderElector.isGlobalLeader()) {
            return;
        }
        
        try {
            int published = publishingService.publishForTopicPublisher(topicName, publisherName);
            outboxMetrics.recordProcessingCycle();
            if (published > 0) {
                log.debug("Published {} events for ({}, {})", published, topicName, publisherName);
            }
        } catch (Exception e) {
            log.error("Error in scheduler for ({}, {})", topicName, publisherName, e);
            outboxMetrics.recordProcessingCycle();
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
}
