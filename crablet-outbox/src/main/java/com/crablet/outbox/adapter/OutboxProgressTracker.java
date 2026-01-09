package com.crablet.outbox.adapter;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventprocessor.progress.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Progress tracker for outbox processors.
 * Uses the outbox_topic_progress table to track position per (topic, publisher) pair.
 * 
 * <p>Uses plain JDBC for consistency with eventstore module and full control.
 */
public class OutboxProgressTracker implements ProgressTracker<TopicPublisherPair> {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxProgressTracker.class);
    
    private final DataSource dataSource;
    
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
    
    public OutboxProgressTracker(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.dataSource = dataSource;
    }
    
    @Override
    public long getLastPosition(TopicPublisherPair processorId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_LAST_POSITION_SQL)) {
            
            stmt.setString(1, processorId.topic());
            stmt.setString(2, processorId.publisher());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long position = rs.getLong("last_position");
                    return rs.wasNull() ? 0L : position;
                }
                return 0L;
            }
        } catch (SQLException e) {
            log.debug("Outbox progress not found for {}, returning 0", processorId, e);
            return 0L;
        }
    }
    
    @Override
    public void updateProgress(TopicPublisherPair processorId, long position) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(UPDATE_LAST_POSITION_SQL)) {
            
            stmt.setLong(1, position);
            stmt.setString(2, processorId.topic());
            stmt.setString(3, processorId.publisher());
            
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                log.warn("No rows updated for progress update: {}", processorId);
            }
        } catch (SQLException e) {
            log.error("Failed to update progress for outbox: {}", processorId, e);
            throw new RuntimeException("Failed to update progress for outbox: " + processorId, e);
        }
    }
    
    @Override
    public void recordError(TopicPublisherPair processorId, String error, int maxErrors) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(INCREMENT_ERROR_COUNT_SQL)) {
            
            stmt.setString(1, error);
            stmt.setInt(2, maxErrors);
            stmt.setString(3, processorId.topic());
            stmt.setString(4, processorId.publisher());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to record error for outbox: {}", processorId, e);
            throw new RuntimeException("Failed to record error for outbox: " + processorId, e);
        }
    }
    
    @Override
    public void resetErrorCount(TopicPublisherPair processorId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(RESET_ERROR_COUNT_SQL)) {
            
            stmt.setString(1, processorId.topic());
            stmt.setString(2, processorId.publisher());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to reset error count for outbox: {}", processorId, e);
            throw new RuntimeException("Failed to reset error count for outbox: " + processorId, e);
        }
    }
    
    @Override
    public ProcessorStatus getStatus(TopicPublisherPair processorId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_STATUS_SQL)) {
            
            stmt.setString(1, processorId.topic());
            stmt.setString(2, processorId.publisher());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String statusStr = rs.getString("status");
                    if (statusStr != null) {
                        return switch (statusStr) {
                            case "ACTIVE" -> ProcessorStatus.ACTIVE;
                            case "PAUSED" -> ProcessorStatus.PAUSED;
                            case "FAILED" -> ProcessorStatus.FAILED;
                            default -> ProcessorStatus.ACTIVE;
                        };
                    }
                }
                return ProcessorStatus.ACTIVE;
            }
        } catch (SQLException e) {
            log.debug("Outbox progress not found for {}, returning ACTIVE", processorId, e);
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
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(UPDATE_STATUS_SQL)) {
            
            stmt.setString(1, statusStr);
            stmt.setString(2, processorId.topic());
            stmt.setString(3, processorId.publisher());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to set status for outbox: {}", processorId, e);
            throw new RuntimeException("Failed to set status for outbox: " + processorId, e);
        }
    }
    
    @Override
    public void autoRegister(TopicPublisherPair processorId, String instanceId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(INSERT_TOPIC_PUBLISHER_SQL)) {
            
            stmt.setString(1, processorId.topic());
            stmt.setString(2, processorId.publisher());
            stmt.setString(3, instanceId);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to auto-register outbox: {}", processorId, e);
            throw new RuntimeException("Failed to auto-register outbox: " + processorId, e);
        }
    }
}

