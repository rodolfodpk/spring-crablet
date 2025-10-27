package com.crablet.outbox.publishing;

import com.crablet.eventstore.store.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.metrics.OutboxMetrics;
import com.crablet.outbox.metrics.OutboxPublisherMetrics;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

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

/**
 * Implementation of OutboxPublishingService.
 * Handles event fetching, publishing, and position updates.
 */
public class OutboxPublishingServiceImpl implements OutboxPublishingService {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxPublishingServiceImpl.class);
    
    private final OutboxConfig config;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource readDataSource;
    private final Map<String, OutboxPublisher> publisherByName;
    private final OutboxMetrics outboxMetrics;
    private final OutboxPublisherMetrics publisherMetrics;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final GlobalStatisticsPublisher globalStatistics;
    
    // SQL statements for outbox operations
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
    
    public OutboxPublishingServiceImpl(
            OutboxConfig config,
            JdbcTemplate jdbcTemplate,
            DataSource readDataSource,
            Map<String, OutboxPublisher> publisherByName,
            OutboxMetrics outboxMetrics,
            OutboxPublisherMetrics publisherMetrics,
            CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics) {
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
        this.readDataSource = readDataSource;
        this.publisherByName = publisherByName;
        this.outboxMetrics = outboxMetrics;
        this.publisherMetrics = publisherMetrics;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.globalStatistics = globalStatistics;
    }
    
    @Override
    public int publishForTopicPublisher(String topicName, String publisherName) {
        // Check if pair is paused or failed
        String status = getPublisherStatus(topicName, publisherName);
        if ("PAUSED".equals(status)) {
            log.trace("Pair ({}, {}) is paused, skipping", topicName, publisherName);
            return 0;
        }
        if ("FAILED".equals(status)) {
            log.warn("Pair ({}, {}) is in FAILED state, skipping", topicName, publisherName);
            return 0;
        }
        
        // Get publisher implementation
        OutboxPublisher publisher = publisherByName.get(publisherName);
        if (publisher == null) {
            log.warn("Publisher '{}' not found for pair ({}, {})", publisherName, topicName, publisherName);
            throw new RuntimeException("Publisher not found: " + publisherName);
        }
        
        // Get topic configuration
        TopicConfig topicConfig = config.getTopics().get(topicName);
        if (topicConfig == null) {
            log.warn("Topic '{}' not found for pair ({}, {})", topicName, topicName, publisherName);
            throw new RuntimeException("Topic not found: " + topicName);
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
            
            // Record events in global statistics
            if (globalStatistics != null) {
                events.forEach(event -> 
                    globalStatistics.recordEvent(topicName, publisherName, event.type()));
            }
            
            return events.size();
        } catch (Exception e) {
            publisherMetrics.recordPublishingError(publisherName);
            outboxMetrics.recordError(publisherName);
            incrementErrorCount(topicName, publisherName, e.getMessage());
            throw new RuntimeException("Failed to publish events for pair (" + topicName + ", " + publisherName + ")", e);
        }
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
            topicName, publisherName, outboxMetrics.getInstanceId()
        );
        
        if (updated != null && updated > 0) {
            log.info("Auto-registered new publisher {} for topic {}", publisherName, topicName);
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
    
    private void resetErrorCount(String topicName, String publisherName) {
        jdbcTemplate.update(
            RESET_ERROR_COUNT_SQL,
            topicName, publisherName
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
    
    /**
     * Fetch events for a specific topic using tag-based filtering.
     * Uses raw JDBC with read-only connection for optimal performance.
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
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setFetchSize(config.getBatchSize());
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
                
                connection.commit();
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
    
    private List<com.crablet.eventstore.store.Tag> parseTagsFromArray(java.sql.Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        
        String[] tagStrings = (String[]) array.getArray();
        List<com.crablet.eventstore.store.Tag> tags = new ArrayList<>();
        
        for (String tagStr : tagStrings) {
            // Format: "key:value"
            int colonIndex = tagStr.indexOf(':');
            if (colonIndex > 0) {
                String key = tagStr.substring(0, colonIndex);
                String value = tagStr.substring(colonIndex + 1);
                tags.add(new com.crablet.eventstore.store.Tag(key, value));
            }
        }
        
        return tags;
    }
}

