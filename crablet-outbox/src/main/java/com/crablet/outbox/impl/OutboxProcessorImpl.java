package com.crablet.outbox.impl;

import com.crablet.core.StoredEvent;
import com.crablet.outbox.OutboxProcessor;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.impl.publishers.GlobalStatisticsPublisher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxProcessorImpl implements OutboxProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorImpl.class);
    
    // Core dependencies
    private final OutboxConfig config;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource readDataSource;
    private final OutboxLeaderElector leaderElector;
    
    // Supporting infrastructure
    private final OutboxMetrics outboxMetrics;
    private final OutboxPublisherMetrics publisherMetrics;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final GlobalStatisticsPublisher globalStatistics;
    
    // Derived fields
    private final String instanceId;
    
    // Track which publishers are available by name
    private final Map<String, OutboxPublisher> publisherByName = new ConcurrentHashMap<>();
    
    // Track when we last attempted to acquire new pairs (for PER_TOPIC_PUBLISHER mode)
    private volatile long lastAcquisitionAttemptMs = 0;
    
    public OutboxProcessorImpl(
            OutboxConfig config,
            JdbcTemplate jdbcTemplate,
            @Qualifier("readDataSource") DataSource readDataSource,
            List<OutboxPublisher> publishers,
            OutboxLeaderElector leaderElector,
            OutboxMetrics outboxMetrics,
            OutboxPublisherMetrics publisherMetrics,
            CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics) {
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
        this.readDataSource = readDataSource;
        this.leaderElector = leaderElector;
        this.outboxMetrics = outboxMetrics;
        this.publisherMetrics = publisherMetrics;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.globalStatistics = globalStatistics;
        this.instanceId = outboxMetrics.getInstanceId();
        
        // Build publisher lookup map
        for (OutboxPublisher publisher : publishers) {
            publisherByName.put(publisher.getName(), publisher);
        }
        
        log.info("Outbox processor initialized with {} publishers", publishers.size());
        log.info("Instance ID: {}", instanceId);
        log.info("Lock strategy: {}", config.getLockStrategy());
        publishers.forEach(p -> log.info("  - {}", p.getName()));
    }
    
    /**
     * SQL statements for outbox operations.
     * Extracted as constants for maintainability and readability.
     */
    private static final String UPDATE_STATUS_PAUSED_SQL = 
        "UPDATE outbox_topic_progress SET status = 'PAUSED', updated_at = CURRENT_TIMESTAMP WHERE topic = ? AND publisher = ?";

    private static final String SELECT_STATUS_SQL = 
        "SELECT status FROM outbox_topic_progress WHERE topic = ? AND publisher = ?";

    private static final String INSERT_TOPIC_PUBLISHER_SQL = """
        INSERT INTO outbox_topic_progress (topic, publisher, last_position, status, leader_instance, leader_heartbeat)
        VALUES (?, ?, 0, 'ACTIVE', ?, CURRENT_TIMESTAMP)
        ON CONFLICT (topic, publisher) DO NOTHING
        """;

    private static final String SELECT_LAST_POSITION_SQL = 
        "SELECT last_position FROM outbox_topic_progress WHERE topic = ? AND publisher = ?";

    private static final String UPDATE_LAST_POSITION_SQL = """
        UPDATE outbox_topic_progress
        SET last_position = ?,
            last_published_at = CURRENT_TIMESTAMP,
            error_count = 0,
            last_error = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE topic = ? AND publisher = ?
        """;

    private static final String INCREMENT_ERROR_COUNT_SQL = """
        UPDATE outbox_topic_progress
        SET error_count = error_count + 1,
            last_error = ?,
            status = CASE 
                WHEN error_count + 1 >= ? THEN 'FAILED'
                ELSE status
            END,
            updated_at = CURRENT_TIMESTAMP
        WHERE topic = ? AND publisher = ?
        """;

    private static final String SELECT_ERROR_COUNT_SQL = 
        "SELECT error_count FROM outbox_topic_progress WHERE topic = ? AND publisher = ?";

    private static final String RESET_ERROR_COUNT_SQL = """
        UPDATE outbox_topic_progress
        SET error_count = 0,
            last_error = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE topic = ? AND publisher = ?
        """;
    
    /**
     * Initialize leader election on startup for both GLOBAL and PER_TOPIC_PUBLISHER modes.
     */
    @PostConstruct
    public void initializeLeaderElection() {
        if (!config.isEnabled()) {
            log.info("Outbox processing is disabled");
            return;
        }
        
        if (config.getLockStrategy() == OutboxConfig.LockStrategy.GLOBAL) {
            leaderElector.tryAcquireGlobalLeader();
        } else if (config.getLockStrategy() == OutboxConfig.LockStrategy.PER_TOPIC_PUBLISHER) {
            leaderElector.initializeAllPairs();
        }
    }
    
    /**
     * Release leader election resources on shutdown for both GLOBAL and PER_TOPIC_PUBLISHER modes.
     */
    @PreDestroy
    public void releaseLeaderElection() {
        if (config.getLockStrategy() == OutboxConfig.LockStrategy.GLOBAL) {
            leaderElector.releaseGlobalLeader();
        } else if (config.getLockStrategy() == OutboxConfig.LockStrategy.PER_TOPIC_PUBLISHER) {
            leaderElector.releaseAllPairs();
        }
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
     * GLOBAL mode: One instance processes all publishers.
     * Uses persistent advisory lock with retry on failure.
     */
    private void pollAndPublishGlobal() {
        // If not leader, try to acquire lock (failover detection)
        if (!leaderElector.isGlobalLeader()) {
            if (!leaderElector.tryAcquireGlobalLeader()) {
                log.trace("Another instance holds the GLOBAL lock, skipping");
                return;
            }
        }
        
        // We're the leader - process events
        try {
            int processed = processPending();
            outboxMetrics.recordProcessingCycle();
            if (processed > 0) {
                log.debug("Processed {} outbox entries as leader", processed);
            }
        } catch (Exception e) {
            log.error("Error processing outbox", e);
        }
        // NOTE: DO NOT release lock - PostgreSQL will auto-release on crash
    }
    
    /**
     * PER_TOPIC_PUBLISHER mode: Each instance processes its owned (topic, publisher) pairs.
     * Periodically tries to acquire new pairs (for dynamic scaling and failover).
     */
    private void pollAndPublishPerTopicPublisher() {
        // Step 1: Update heartbeat for pairs we own (proves we're alive)
        leaderElector.updateHeartbeats();
        
        // Step 2: Periodically try to acquire new/abandoned pairs (throttled for efficiency)
        long now = System.currentTimeMillis();
        if (now - lastAcquisitionAttemptMs >= config.getAcquisitionRetryIntervalMs()) {
            leaderElector.tryAcquireAvailablePairs();
            lastAcquisitionAttemptMs = now;
        }
        
        // Step 3: Process owned pairs
        Set<TopicPublisherPair> ownedPairs = leaderElector.getOwnedPairs();
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
            UPDATE_STATUS_PAUSED_SQL,
            pair.topic(), pair.publisher()
        );
        
        // Record metrics for alerting
        outboxMetrics.recordAutoPause(pair.topic(), pair.publisher(), errorCount, lastError);
    }
    
    /**
     * Fetch events for a specific topic using tag-based filtering.
     * Uses raw JDBC with read-only connection for optimal performance.
     * 
     * <p>This is the hot path for outbox processing (called every poll cycle).
     * Raw JDBC provides:
     * <ul>
     *   <li>Direct control over connection (read-only marking)</li>
     *   <li>Explicit fetch size for streaming</li>
     *   <li>~2-5% better performance than JdbcTemplate</li>
     * </ul>
     * 
     * @param topicConfig Topic configuration with tag filters
     * @param lastPosition Last processed event position
     * @param limit Maximum events to fetch
     * @return List of events matching topic filters after lastPosition
     */
    private List<StoredEvent> fetchEventsForTopic(TopicConfig topicConfig, long lastPosition, int limit) {
        String sqlFilter = buildSqlFilterForTopic(topicConfig);
        String sql = """
            SELECT type, tags, data, transaction_id, position, occurred_at
            FROM events
            WHERE position > ?
              AND (%s)
            ORDER BY position ASC
            LIMIT ?
            """.formatted(sqlFilter);
        
        try (Connection connection = readDataSource.getConnection()) {
            connection.setReadOnly(true);  // Read-only operation
            connection.setAutoCommit(false);  // For streaming large result sets
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setFetchSize(config.getBatchSize());  // Stream results efficiently
                stmt.setLong(1, lastPosition);
                stmt.setInt(2, limit);
                
                List<StoredEvent> events = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        events.add(new StoredEvent(
                            rs.getString("type"),
                            parseTagsFromArray(rs.getArray("tags")),
                            rs.getString("data").getBytes(),
                            rs.getString("transaction_id"),
                            rs.getLong("position"),
                            rs.getTimestamp("occurred_at").toInstant()
                        ));
                    }
                }
                
                connection.commit();  // Commit read-only transaction
                log.debug("Fetched {} events for topic after position {}", events.size(), lastPosition);
                return events;
                
            } catch (Exception e) {
                connection.rollback();
                log.error("Failed to fetch events for topic: {}", e.getMessage());
                throw new RuntimeException("Failed to fetch events for topic", e);
            }
        } catch (SQLException e) {
            log.error("Failed to get connection for event fetch: {}", e.getMessage());
            throw new RuntimeException("Failed to get connection for event fetch", e);
        }
    }
    
    /**
     * Generate SQL filter for topic tag matching.
     * Uses PostgreSQL array operators for efficiency.
     */
    private String buildSqlFilterForTopic(TopicConfig topicConfig) {
        Set<String> requiredTags = topicConfig.getRequiredTags();
        Set<String> anyOfTags = topicConfig.getAnyOfTags();
        Map<String, String> exactTags = topicConfig.getExactTags();
        
        if (requiredTags.isEmpty() && anyOfTags.isEmpty() && exactTags.isEmpty()) {
            return "TRUE"; // Match all
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Required tags: ALL must be present
        for (String tag : requiredTags) {
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tag + ":%')");
        }
        
        // AnyOf tags: at least ONE must be present
        if (!anyOfTags.isEmpty()) {
            String anyOfCondition = anyOfTags.stream()
                .map(tag -> "t LIKE '" + tag + ":%'")
                .collect(java.util.stream.Collectors.joining(" OR "));
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");
        }
        
        // Exact matches
        for (var entry : exactTags.entrySet()) {
            conditions.add("'" + entry.getKey() + ":" + entry.getValue() + "' = ANY(tags)");
        }
        
        return String.join(" AND ", conditions);
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
                SELECT_STATUS_SQL,
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
            INSERT_TOPIC_PUBLISHER_SQL,
            topicName, publisherName, instanceId
        );
        
        if (updated != null && updated > 0) {
            log.info("Auto-registered new publisher {} for topic {}", publisherName, topicName);
            // Also try to acquire the pair since we're auto-registering it
            TopicPublisherPair pair = new TopicPublisherPair(topicName, publisherName);
            leaderElector.tryAcquirePair(pair);
        }
        
        return jdbcTemplate.queryForObject(
            SELECT_LAST_POSITION_SQL,
            Long.class,
            topicName, publisherName
        );
    }
    
    private void updateLastPosition(String topicName, String publisherName, long newPosition) {
        jdbcTemplate.update(
            UPDATE_LAST_POSITION_SQL,
            newPosition, topicName, publisherName
        );
    }
    
    private void incrementErrorCount(String topicName, String publisherName, String error) {
        jdbcTemplate.update(
            INCREMENT_ERROR_COUNT_SQL,
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
            Integer count = jdbcTemplate.queryForObject(
                SELECT_ERROR_COUNT_SQL,
                Integer.class,
                topicName, publisherName
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0; // Publisher not registered yet
        }
    }
    
    private void resetErrorCount(String topicName, String publisherName) {
        jdbcTemplate.update(
            RESET_ERROR_COUNT_SQL,
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
