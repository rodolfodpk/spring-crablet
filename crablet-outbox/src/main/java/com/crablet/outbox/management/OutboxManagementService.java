package com.crablet.outbox.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for managing outbox publisher operations.
 * Provides high-level operations for pausing, resuming, and monitoring publishers.
 */
@Service
public class OutboxManagementService {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxManagementService.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final com.crablet.outbox.processor.OutboxProcessorImpl outboxProcessor;
    
    public OutboxManagementService(JdbcTemplate jdbcTemplate, com.crablet.outbox.processor.OutboxProcessorImpl outboxProcessor) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxProcessor = outboxProcessor;
    }
    
    /**
     * Pause a publisher (stops processing events).
     * 
     * @param publisherName Name of the publisher to pause
     * @return true if publisher was paused, false if not found
     */
    public boolean pausePublisher(String publisherName) {
        int updated = jdbcTemplate.update(
            "UPDATE outbox_topic_progress SET status = 'PAUSED', updated_at = CURRENT_TIMESTAMP WHERE topic = 'default' AND publisher = ?",
            publisherName
        );
        
        if (updated > 0) {
            log.info("Publisher {} paused successfully", publisherName);
            return true;
        } else {
            log.warn("Publisher {} not found for pause operation", publisherName);
            return false;
        }
    }
    
    /**
     * Resume a publisher (starts processing events again).
     * 
     * @param publisherName Name of the publisher to resume
     * @return true if publisher was resumed, false if not found
     */
    public boolean resumePublisher(String publisherName) {
        int updated = jdbcTemplate.update(
            "UPDATE outbox_topic_progress SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP WHERE topic = 'default' AND publisher = ?",
            publisherName
        );
        
        if (updated > 0) {
            log.info("Publisher {} resumed successfully", publisherName);
            return true;
        } else {
            log.warn("Publisher {} not found for resume operation", publisherName);
            return false;
        }
    }
    
    /**
     * Reset a failed publisher (clears error count and resumes).
     * 
     * @param publisherName Name of the publisher to reset
     * @return true if publisher was reset, false if not found
     */
    public boolean resetPublisher(String publisherName) {
        int updated = jdbcTemplate.update(
            """
            UPDATE outbox_topic_progress 
            SET status = 'ACTIVE',
                error_count = 0,
                last_error = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE topic = 'default' AND publisher = ?
            """,
            publisherName
        );
        
        if (updated > 0) {
            log.info("Publisher {} reset successfully", publisherName);
            return true;
        } else {
            log.warn("Publisher {} not found for reset operation", publisherName);
            return false;
        }
    }
    
    /**
     * Get status of all publishers.
     * 
     * @return List of publisher status information
     */
    public List<PublisherStatus> getAllPublisherStatus() {
        return jdbcTemplate.query(
            """
            SELECT publisher, status, last_position, last_published_at, 
                error_count, last_error, updated_at
            FROM outbox_topic_progress
            WHERE topic = 'default'
            ORDER BY publisher
            """,
            (rs, _) -> new PublisherStatus(
                rs.getString("publisher"),
                rs.getString("status"),
                rs.getLong("last_position"),
                rs.getTimestamp("last_published_at") != null 
                    ? rs.getTimestamp("last_published_at").toInstant() 
                    : null,
                rs.getInt("error_count"),
                rs.getString("last_error"),
                rs.getTimestamp("updated_at").toInstant()
            )
        );
    }
    
    /**
     * Get status of a specific publisher.
     * 
     * @param publisherName Name of the publisher
     * @return Publisher status or null if not found
     */
    public PublisherStatus getPublisherStatus(String publisherName) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT publisher, status, last_position, last_published_at, 
                    error_count, last_error, updated_at
                FROM outbox_topic_progress
                WHERE topic = 'default' AND publisher = ?
                """,
                (rs, _) -> new PublisherStatus(
                    rs.getString("publisher"),
                    rs.getString("status"),
                    rs.getLong("last_position"),
                    rs.getTimestamp("last_published_at") != null 
                        ? rs.getTimestamp("last_published_at").toInstant() 
                        : null,
                    rs.getInt("error_count"),
                    rs.getString("last_error"),
                    rs.getTimestamp("updated_at").toInstant()
                ),
                publisherName
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get publisher lag (how far behind the latest event).
     * 
     * @return Map of publisher name to lag (current max position - last published position)
     */
    public Map<String, Long> getPublisherLag() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT 
                otp.publisher,
                COALESCE(MAX(e.position) - otp.last_position, 0) as lag
            FROM outbox_topic_progress otp
            LEFT JOIN events e ON e.position > otp.last_position
            WHERE otp.topic = 'default'
            GROUP BY otp.publisher, otp.last_position
            ORDER BY otp.publisher
            """
        );
        
        return rows.stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> (String) row.get("publisher"),
                row -> ((Number) row.get("lag")).longValue()
            ));
    }
    
    /**
     * Check if publisher exists.
     * 
     * @param publisherName Name of the publisher
     * @return true if publisher exists, false otherwise
     */
    public boolean publisherExists(String publisherName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'default' AND publisher = ?",
            Integer.class,
            publisherName
        );
        return count != null && count > 0;
    }
    
    /**
     * Get current leader instances for all publishers.
     * 
     * @return Map of publisher name to leader instance ID
     */
    public Map<String, String> getCurrentLeaders() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT publisher, leader_instance
            FROM outbox_topic_progress
            WHERE topic = 'default' AND leader_instance IS NOT NULL
            ORDER BY publisher
            """
        );
        
        return rows.stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> (String) row.get("publisher"),
                row -> (String) row.get("leader_instance")
            ));
    }
    
    /**
     * Get backoff information for all publishers.
     */
    public Map<String, BackoffInfo> getBackoffInfo() {
        Map<String, BackoffInfo> result = new java.util.HashMap<>();
        
        Map<String, com.crablet.outbox.processor.BackoffState> backoffStates = outboxProcessor.getAllBackoffStates();
        for (var entry : backoffStates.entrySet()) {
            com.crablet.outbox.processor.BackoffState state = entry.getValue();
            result.put(entry.getKey(), new BackoffInfo(
                state.getEmptyPollCount(),
                state.getCurrentSkipCounter()
            ));
        }
        
        return result;
    }
    
    /**
     * Get backoff information for a specific publisher.
     */
    public BackoffInfo getBackoffInfo(String topic, String publisher) {
        com.crablet.outbox.processor.BackoffState state = outboxProcessor.getBackoffState(topic, publisher);
        
        if (state == null) {
            return null;
        }
        
        return new BackoffInfo(
            state.getEmptyPollCount(),
            state.getCurrentSkipCounter()
        );
    }
    
    /**
     * Publisher status information.
     */
    public record PublisherStatus(
        String publisherName,
        String status,
        long lastPosition,
        Instant lastPublishedAt,
        int errorCount,
        String lastError,
        Instant updatedAt
    ) {
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
        
        public boolean isPaused() {
            return "PAUSED".equals(status);
        }
        
        public boolean isFailed() {
            return "FAILED".equals(status);
        }
    }
    
    /**
     * Backoff information for a publisher.
     */
    public record BackoffInfo(
        int emptyPollCount,
        int currentSkipCounter
    ) {
        public boolean isBackedOff() {
            return currentSkipCounter > 0;
        }
    }
}
