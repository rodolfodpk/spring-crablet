package com.crablet.outbox.adapter;

import com.crablet.eventprocessor.progress.ProgressTracker;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Progress tracker for outbox processors.
 * Uses the outbox_topic_progress table to track position per (topic, publisher) pair.
 */
public class OutboxProgressTracker implements ProgressTracker<TopicPublisherPair> {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxProgressTracker.class);
    
    private final JdbcTemplate jdbcTemplate;
    
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
    
    private static final String RESET_ERROR_COUNT_SQL = """
        UPDATE outbox_topic_progress
        SET error_count = 0,
            last_error = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE topic = ? AND publisher = ?
        """;
    
    private static final String SELECT_STATUS_SQL = 
        "SELECT status FROM outbox_topic_progress WHERE topic = ? AND publisher = ?";
    
    private static final String UPDATE_STATUS_SQL = 
        "UPDATE outbox_topic_progress SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE topic = ? AND publisher = ?";
    
    private static final String INSERT_TOPIC_PUBLISHER_SQL = """
        INSERT INTO outbox_topic_progress (topic, publisher, last_position, status, leader_instance, leader_heartbeat)
        VALUES (?, ?, 0, 'ACTIVE', ?, CURRENT_TIMESTAMP)
        ON CONFLICT (topic, publisher) DO NOTHING
        """;
    
    public OutboxProgressTracker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public long getLastPosition(TopicPublisherPair processorId) {
        try {
            Long position = jdbcTemplate.queryForObject(
                SELECT_LAST_POSITION_SQL,
                Long.class,
                processorId.topic(), processorId.publisher()
            );
            return position != null ? position : 0L;
        } catch (Exception e) {
            // Publisher not registered yet, return 0
            return 0L;
        }
    }
    
    @Override
    public void updateProgress(TopicPublisherPair processorId, long position) {
        int updated = jdbcTemplate.update(
            UPDATE_LAST_POSITION_SQL,
            position,
            processorId.topic(), processorId.publisher()
        );
        
        if (updated == 0) {
            log.warn("No rows updated for progress update: {}", processorId);
        }
    }
    
    @Override
    public void recordError(TopicPublisherPair processorId, String error, int maxErrors) {
        jdbcTemplate.update(
            INCREMENT_ERROR_COUNT_SQL,
            error,
            maxErrors,
            processorId.topic(), processorId.publisher()
        );
    }
    
    @Override
    public void resetErrorCount(TopicPublisherPair processorId) {
        jdbcTemplate.update(
            RESET_ERROR_COUNT_SQL,
            processorId.topic(), processorId.publisher()
        );
    }
    
    @Override
    public ProcessorStatus getStatus(TopicPublisherPair processorId) {
        try {
            String statusStr = jdbcTemplate.queryForObject(
                SELECT_STATUS_SQL,
                String.class,
                processorId.topic(), processorId.publisher()
            );
            
            if (statusStr == null) {
                return ProcessorStatus.ACTIVE; // Default
            }
            
            return switch (statusStr) {
                case "ACTIVE" -> ProcessorStatus.ACTIVE;
                case "PAUSED" -> ProcessorStatus.PAUSED;
                case "FAILED" -> ProcessorStatus.FAILED;
                default -> ProcessorStatus.ACTIVE;
            };
        } catch (Exception e) {
            // Publisher not registered yet, return ACTIVE
            return ProcessorStatus.ACTIVE;
        }
    }
    
    @Override
    public void setStatus(TopicPublisherPair processorId, ProcessorStatus status) {
        String statusStr = switch (status) {
            case ACTIVE -> "ACTIVE";
            case PAUSED -> "PAUSED";
            case FAILED -> "FAILED";
        };
        
        jdbcTemplate.update(
            UPDATE_STATUS_SQL,
            statusStr,
            processorId.topic(), processorId.publisher()
        );
    }
    
    @Override
    public void autoRegister(TopicPublisherPair processorId, String instanceId) {
        jdbcTemplate.update(
            INSERT_TOPIC_PUBLISHER_SQL,
            processorId.topic(), processorId.publisher(), instanceId
        );
    }
}

