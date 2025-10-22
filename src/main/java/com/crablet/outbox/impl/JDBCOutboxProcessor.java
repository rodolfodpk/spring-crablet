package com.crablet.outbox.impl;

import com.crablet.core.StoredEvent;
import com.crablet.outbox.*;
import com.crablet.outbox.impl.publishers.GlobalStatisticsPublisher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class JDBCOutboxProcessor implements OutboxProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(JDBCOutboxProcessor.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final OutboxConfig config;
    private final List<OutboxPublisher> publishers;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final OutboxMetrics outboxMetrics;
    private final OutboxPublisherMetrics publisherMetrics;
    private final String instanceId;
    private final GlobalStatisticsPublisher globalStatistics;
    
    // Track which (topic, publisher) pairs this instance currently owns (for PER_TOPIC_PUBLISHER mode)
    private final Set<TopicPublisherPair> ownedPairs = ConcurrentHashMap.newKeySet();
    private final Map<String, OutboxPublisher> publisherByName = new ConcurrentHashMap<>();
    
    public JDBCOutboxProcessor(
            JdbcTemplate jdbcTemplate, 
            OutboxConfig config,
            List<OutboxPublisher> publishers,
            CircuitBreakerRegistry circuitBreakerRegistry,
            OutboxMetrics outboxMetrics,
            OutboxPublisherMetrics publisherMetrics,
            GlobalStatisticsPublisher globalStatistics) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
        this.publishers = publishers;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.outboxMetrics = outboxMetrics;
        this.publisherMetrics = publisherMetrics;
        this.instanceId = outboxMetrics.getInstanceId();
        this.globalStatistics = globalStatistics;
        
        // Build publisher lookup map
        for (OutboxPublisher publisher : publishers) {
            publisherByName.put(publisher.getName(), publisher);
        }
        
        log.info("Outbox processor initialized with {} publishers", publishers.size());
        log.info("Instance ID: {}", instanceId);
        log.info("Lock strategy: {}", config.getLockStrategy());
        publishers.forEach(p -> log.info("  - {}", p.getName()));
    }
    
    // Advisory lock key for outbox coordination (hash of "crablet-outbox-processor")
    private static final long OUTBOX_LOCK_KEY = 4856221667890123456L;
    
    /**
     * Initialize (topic, publisher) pair ownership on startup (for PER_TOPIC_PUBLISHER mode).
     * Tries to acquire locks for each configured (topic, publisher) pair once during initialization.
     */
    @PostConstruct
    public void initializeTopicPublisherOwnership() {
        if (config.getLockStrategy() != OutboxConfig.LockStrategy.PER_TOPIC_PUBLISHER) {
            return; // Other modes don't use this
        }
        
        log.info("Attempting to acquire (topic, publisher) pair locks (PER_TOPIC_PUBLISHER mode)...");
        
        // Build all (topic, publisher) pairs from configuration
        Map<String, TopicConfig> topicConfigs = config.getTopics();
        
        for (var entry : topicConfigs.entrySet()) {
            String topicName = entry.getKey();
            TopicConfig topicConfig = entry.getValue();
            
            // Get publishers for this topic from configuration
            Set<String> configuredPublishers = topicConfig.getPublishers();
            
            for (String publisherName : configuredPublishers) {
                TopicPublisherPair pair = new TopicPublisherPair(topicName, publisherName);
                long lockKey = pair.getLockKey();
                
                Boolean acquired = tryAcquireLock(lockKey);
                if (Boolean.TRUE.equals(acquired)) {
                    ownedPairs.add(pair);
                    updatePairLeaderInDatabase(pair, instanceId);
                    
                    log.info("✓ Acquired ownership of pair '{}'", pair);
                } else {
                    log.info("✗ Pair '{}' is owned by another instance", pair);
                }
            }
        }
        
        log.info("This instance owns {} of {} total pairs", ownedPairs.size(),
            topicConfigs.values().stream().mapToInt(t -> t.getPublishers().size()).sum());
    }
    
    /**
     * Release (topic, publisher) pair ownership on shutdown (for PER_TOPIC_PUBLISHER mode).
     * Releases all owned locks gracefully.
     */
    @PreDestroy
    public void releaseTopicPublisherOwnership() {
        if (config.getLockStrategy() != OutboxConfig.LockStrategy.PER_TOPIC_PUBLISHER) {
            return;
        }
        
        log.info("Releasing (topic, publisher) pair ownership...");
        
        for (TopicPublisherPair pair : ownedPairs) {
            long lockKey = pair.getLockKey();
            releaseLock(lockKey);
            clearPairLeaderInDatabase(pair);
            outboxMetrics.setLeaderForPublisher(pair.topic(), false);
            log.info("Released ownership of pair: {}", pair);
        }
        
        ownedPairs.clear();
        log.info("All pair locks released");
    }
    
    /**
     * Get the lock key for a topic (strategy-aware).
     */
    private long getLockKey(String topicName) {
        if (config.getLockStrategy() == OutboxConfig.LockStrategy.GLOBAL) {
            return OUTBOX_LOCK_KEY; // Global lock
        }
        // Per-topic lock: hash of "crablet-outbox-topic-{name}"
        return ("crablet-outbox-topic-" + topicName).hashCode();
    }
    
    /**
     * Get the lock key for a (topic, publisher) pair.
     */
    private long getLockKeyForPair(TopicPublisherPair pair) {
        return pair.getLockKey();
    }
    
    /**
     * Update leader instance in database for a (topic, publisher) pair.
     */
    private void updatePairLeaderInDatabase(TopicPublisherPair pair, String instanceId) {
        // This will be updated when we implement the new database schema
        // For now, we'll use a placeholder that works with the current schema
        log.debug("Updating leader for pair {} to instance {}", pair, instanceId);
    }
    
    /**
     * Clear leader instance in database for a (topic, publisher) pair.
     */
    private void clearPairLeaderInDatabase(TopicPublisherPair pair) {
        // This will be updated when we implement the new database schema
        // For now, we'll use a placeholder that works with the current schema
        log.debug("Clearing leader for pair {}", pair);
    }
    
    /**
     * Try to acquire an advisory lock (non-blocking).
     */
    private Boolean tryAcquireLock(long lockKey) {
        return jdbcTemplate.queryForObject(
            "SELECT pg_try_advisory_lock(?)",
            Boolean.class,
            lockKey
        );
    }
    
    /**
     * Release an advisory lock.
     */
    private void releaseLock(long lockKey) {
        jdbcTemplate.execute("SELECT pg_advisory_unlock(" + lockKey + ")");
    }
    
    @Scheduled(fixedDelayString = "${crablet.outbox.polling-interval-ms:1000}")
    public void pollAndPublish() {
        if (!config.isEnabled()) {
            return;
        }
        
        if (config.getLockStrategy() == OutboxConfig.LockStrategy.GLOBAL) {
            pollAndPublishGlobal();
        } else if (config.getLockStrategy() == OutboxConfig.LockStrategy.PER_TOPIC_PUBLISHER) {
            pollAndPublishPerTopicPublisher();
        }
    }
    
    /**
     * GLOBAL mode: Acquire single lock, process all publishers.
     */
    private void pollAndPublishGlobal() {
        // Try to acquire advisory lock (session-level, non-blocking)
        // Only one instance across all application nodes can hold this lock
        Boolean acquired = tryAcquireLock(OUTBOX_LOCK_KEY);
        
        if (Boolean.FALSE.equals(acquired)) {
            log.trace("Another instance holds the outbox lock, skipping");
            outboxMetrics.setLeader(false);  // Not leader
            return;
        }
        
        outboxMetrics.setLeader(true);  // This instance is leader
        
        try {
            int processed = processPending();
            outboxMetrics.recordProcessingCycle();
            if (processed > 0) {
                log.debug("Processed {} outbox entries as leader", processed);
            }
        } catch (Exception e) {
            log.error("Error processing outbox", e);
        } finally {
            // Always release lock, even on exception
            // If process crashes, PostgreSQL releases lock automatically
            releaseLock(OUTBOX_LOCK_KEY);
        }
    }
    
    /**
     * PER_TOPIC_PUBLISHER mode: Process only (topic, publisher) pairs owned by this instance.
     * Each pair is processed independently with fault isolation.
     */
    private void pollAndPublishPerTopicPublisher() {
        if (ownedPairs.isEmpty()) {
            log.trace("This instance owns no (topic, publisher) pairs, skipping");
            return;
        }
        
        try {
            int totalProcessed = 0;
            
            for (TopicPublisherPair pair : ownedPairs) {
                try {
                    int processed = processPair(pair);
                    totalProcessed += processed;
                } catch (Exception e) {
                    log.error("Error processing pair {}", pair, e);
                    incrementErrorCount(pair.topic(), pair.publisher(), e.getMessage());
                    
                    // Check if we should auto-pause this pair
                    int errorCount = getErrorCount(pair.topic(), pair.publisher());
                    if (errorCount >= config.getMaxRetries()) {
                        autoPausePair(pair, errorCount, e.getMessage());
                    }
                }
            }
            
            outboxMetrics.recordProcessingCycle();
            if (totalProcessed > 0) {
                log.debug("Processed {} events across {} owned pairs", totalProcessed, ownedPairs.size());
            }
        } catch (Exception e) {
            log.error("Error in PER_TOPIC_PUBLISHER processing", e);
        }
    }
    
    /**
     * Process a single (topic, publisher) pair with independent error handling.
     */
    private int processPair(TopicPublisherPair pair) {
        String topicName = pair.topic();
        String publisherName = pair.publisher();
        
        // Check if pair is paused or failed
        String status = getPublisherStatus(topicName, publisherName);
        if ("PAUSED".equals(status)) {
            log.trace("Pair {} is paused, skipping", pair);
            return 0;
        }
        if ("FAILED".equals(status)) {
            log.warn("Pair {} is in FAILED state, skipping", pair);
            return 0;
        }
        
        // Get publisher implementation
        OutboxPublisher publisher = publisherByName.get(publisherName);
        if (publisher == null) {
            log.warn("Publisher '{}' not found for pair {}", publisherName, pair);
            return 0;
        }
        
        // Get topic configuration
        TopicConfig topicConfig = config.getTopics().get(topicName);
        if (topicConfig == null) {
            log.warn("Topic '{}' not found for pair {}", topicName, pair);
            return 0;
        }
        
        Long lastPosition = getLastPosition(topicName, publisherName);
        List<StoredEvent> events = fetchEventsForTopic(topicConfig, lastPosition, config.getBatchSize());
        
        if (events.isEmpty()) {
            return 0;
        }
        
        // Publish with resilience
        Timer.Sample sample = publisherMetrics.startPublishing(publisherName);
        try {
            publishWithResilience(publisher, events);
            updateLastPosition(topicName, publisherName, events.get(events.size() - 1).position());
            publisherMetrics.recordPublishingSuccess(publisherName, sample, events.size());
            outboxMetrics.recordEventsPublished(publisherName, events.size());
            
            // Reset error count on successful publish
            resetErrorCount(topicName, publisherName);
            
            return events.size();
        } catch (Exception e) {
            publisherMetrics.recordPublishingError(publisherName);
            outboxMetrics.recordError(publisherName);
            throw new RuntimeException("Failed to publish events for pair " + pair, e);
        }
    }
    
    /**
     * Auto-pause a pair when max retries exceeded.
     * This preserves event ordering by stopping processing rather than skipping events.
     */
    private void autoPausePair(TopicPublisherPair pair, int errorCount, String lastError) {
        log.error("ALERT: Auto-pausing pair {} after {} failed attempts. Last error: {}", 
            pair, errorCount, lastError);
        
        // Update status to PAUSED in database
        jdbcTemplate.update(
            "UPDATE outbox_topic_progress SET status = 'PAUSED', updated_at = CURRENT_TIMESTAMP WHERE topic = ? AND publisher = ?",
            pair.topic(), pair.publisher()
        );
        
        // Record metrics for alerting
        outboxMetrics.recordAutoPause(pair.topic(), pair.publisher(), errorCount, lastError);
    }
    
    /**
     * Fetch events for a specific topic using tag-based filtering.
     */
    private List<StoredEvent> fetchEventsForTopic(TopicConfig topicConfig, long lastPosition, int limit) {
        String sql = """
            SELECT type, tags, data, transaction_id, position, occurred_at
            FROM events
            WHERE position > ?
              AND (%s)
            ORDER BY position ASC
            LIMIT ?
            """.formatted(topicConfig.getSqlFilter());
        
        return jdbcTemplate.query(
            sql,
            (rs, _) -> new StoredEvent(
                rs.getString("type"),
                parseTagsFromArray(rs.getArray("tags")),
                rs.getString("data").getBytes(),
                rs.getString("transaction_id"),
                rs.getLong("position"),
                rs.getTimestamp("occurred_at").toInstant()
            ),
            lastPosition,
            limit
        );
    }
    
    @Override
    public int processPending() {
        int totalProcessed = 0;
        
        // Process all configured (topic, publisher) pairs
        Map<String, TopicConfig> topicConfigs = config.getTopics();
        
        for (var topicEntry : topicConfigs.entrySet()) {
            String topicName = topicEntry.getKey();
            TopicConfig topicConfig = topicEntry.getValue();
            
            // Get publishers configured for this topic
            Set<String> configuredPublishers = topicConfig.getPublishers();
            
            for (String publisherName : configuredPublishers) {
                // Get publisher implementation
                OutboxPublisher publisher = publisherByName.get(publisherName);
                if (publisher == null) {
                    log.warn("Publisher '{}' not found for topic '{}'", publisherName, topicName);
                    continue;
                }
                
                try {
                    // Check if publisher is paused for this topic
                    String status = getPublisherStatus(topicName, publisherName);
                    if ("PAUSED".equals(status)) {
                        log.debug("Publisher {} for topic {} is paused, skipping", publisherName, topicName);
                        continue;
                    }
                    
                    if ("FAILED".equals(status)) {
                        log.warn("Publisher {} for topic {} is in FAILED state, skipping", publisherName, topicName);
                        continue;
                    }
                    
                    // Get publisher's last position for this topic
                    Long lastPosition = getLastPosition(topicName, publisherName);
                    
                    // Fetch events after last position for this topic
                    List<StoredEvent> events = fetchEventsForTopic(topicConfig, lastPosition, config.getBatchSize());
                    
                    if (events.isEmpty()) {
                        continue;
                    }
                    
                    // Publish with resilience (circuit breaker + time limiter)
                    Timer.Sample sample = publisherMetrics.startPublishing(publisherName);
                    try {
                        if (publisher.getPreferredMode() == OutboxPublisher.PublishMode.BATCH) {
                            publishWithResilience(publisher, events);
                            updateLastPosition(topicName, publisherName, events.get(events.size() - 1).position());
                            publisherMetrics.recordPublishingSuccess(publisherName, sample, events.size());
                            outboxMetrics.recordEventsPublished(publisherName, events.size());
                            totalProcessed += events.size();
                            
                            // Record events in global statistics
                            if (globalStatistics != null) {
                                events.forEach(event -> 
                                    globalStatistics.recordEvent(topicName, publisherName, event.type()));
                            }
                        } else {
                            // Individual mode: publish one at a time
                            for (StoredEvent event : events) {
                                try {
                                    publishWithResilience(publisher, List.of(event));
                                    updateLastPosition(topicName, publisherName, event.position());
                                    publisherMetrics.recordPublishingSuccess(publisherName, sample, 1);
                                    outboxMetrics.recordEventsPublished(publisherName, 1);
                                    totalProcessed++;
                                    
                                    // Record event in global statistics
                                    if (globalStatistics != null) {
                                        globalStatistics.recordEvent(topicName, publisherName, event.type());
                                    }
                                } catch (PublishException e) {
                                    log.error("Failed to publish event at position {} to publisher {} for topic {}", 
                                        event.position(), publisherName, topicName);
                                    publisherMetrics.recordPublishingError(publisherName);
                                    outboxMetrics.recordError(publisherName);
                                    incrementErrorCount(topicName, publisherName, e.getMessage());
                                    break; // Stop on first failure
                                }
                            }
                        }
                    } catch (Exception e) {
                        publisherMetrics.recordPublishingError(publisherName);
                        outboxMetrics.recordError(publisherName);
                        throw e;
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing publisher {} for topic {}: {}", publisherName, topicName, e.getMessage());
                    incrementErrorCount(topicName, publisherName, e.getMessage());
                }
            }
        }
        
        return totalProcessed;
    }
    
    private String getPublisherStatus(String topicName, String publisherName) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_topic_progress WHERE topic = ? AND publisher = ?",
                String.class,
                topicName, publisherName
            );
        } catch (Exception e) {
            // Publisher not registered yet, return ACTIVE by default
            return "ACTIVE";
        }
    }
    
    private Long getLastPosition(String topicName, String publisherName) {
        // Auto-register publisher for topic if not exists
        Integer updated = jdbcTemplate.update(
            """
            INSERT INTO outbox_topic_progress (topic, publisher, last_position, status)
            VALUES (?, ?, 0, 'ACTIVE')
            ON CONFLICT (topic, publisher) DO NOTHING
            """,
            topicName, publisherName
        );
        
        if (updated > 0) {
            log.info("Auto-registered new publisher {} for topic {}", publisherName, topicName);
        }
        
        return jdbcTemplate.queryForObject(
            "SELECT last_position FROM outbox_topic_progress WHERE topic = ? AND publisher = ?",
            Long.class,
            topicName, publisherName
        );
    }
    
    private void updateLastPosition(String topicName, String publisherName, long newPosition) {
        jdbcTemplate.update(
            """
            UPDATE outbox_topic_progress
            SET last_position = ?,
                last_published_at = CURRENT_TIMESTAMP,
                error_count = 0,
                last_error = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE topic = ? AND publisher = ?
            """,
            newPosition, topicName, publisherName
        );
    }
    
    private void incrementErrorCount(String topicName, String publisherName, String error) {
        jdbcTemplate.update(
            """
            UPDATE outbox_topic_progress
            SET error_count = error_count + 1,
                last_error = ?,
                status = CASE 
                    WHEN error_count + 1 >= ? THEN 'FAILED'
                    ELSE status
                END,
                updated_at = CURRENT_TIMESTAMP
            WHERE topic = ? AND publisher = ?
            """,
            error, config.getMaxRetries(), topicName, publisherName
        );
    }
    
    private void publishWithResilience(OutboxPublisher publisher, List<StoredEvent> events)
            throws PublishException {
        
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("outbox-" + publisher.getName());
        
        try {
            Callable<Void> call = CircuitBreaker.decorateCallable(cb, () -> {
                publisher.publishBatch(events);
                return null;
            });
            
            call.call();
            
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for {}", publisher.getName());
            throw new PublishException("Circuit breaker open", e);
        } catch (Exception e) {
            throw new PublishException("Publish failed", e);
        }
    }
    
    private int getErrorCount(String topicName, String publisherName) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT error_count FROM outbox_topic_progress WHERE topic = ? AND publisher = ?",
                Integer.class,
                topicName, publisherName
            );
        } catch (Exception e) {
            return 0; // Publisher not registered yet
        }
    }
    
    private void resetErrorCount(String topicName, String publisherName) {
        jdbcTemplate.update(
            """
            UPDATE outbox_topic_progress
            SET error_count = 0,
                last_error = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE topic = ? AND publisher = ?
            """,
            topicName, publisherName
        );
    }
    
    private List<com.crablet.core.Tag> parseTagsFromArray(java.sql.Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        
        String[] tagStrings = (String[]) array.getArray();
        List<com.crablet.core.Tag> tags = new ArrayList<>();
        
        for (String tagStr : tagStrings) {
            // Format: "key:value"
            int colonIndex = tagStr.indexOf(':');
            if (colonIndex > 0) {
                String key = tagStr.substring(0, colonIndex);
                String value = tagStr.substring(colonIndex + 1);
                tags.add(new com.crablet.core.Tag(key, value));
            }
        }
        
        return tags;
    }
    
    // Note: markPublished() and markFailed() are no longer needed with publisher-offset-tracking
    // These are kept for interface compatibility but not used
    @Override
    public void markPublished(long outboxId) {
        // No-op: publisher-offset-tracking updates last_position directly
    }
    
    @Override
    public void markFailed(long outboxId, String error) {
        // No-op: publisher-offset-tracking updates error_count directly
    }
}
