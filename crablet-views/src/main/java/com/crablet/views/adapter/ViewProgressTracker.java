package com.crablet.views.adapter;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventprocessor.progress.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Adapter that implements ProgressTracker<String> for view projections.
 * Uses the view_progress table to track processing progress per view.
 * 
 * <p>Uses plain JDBC for consistency with eventstore module and full control.
 */
public class ViewProgressTracker implements ProgressTracker<String> {
    
    private static final Logger log = LoggerFactory.getLogger(ViewProgressTracker.class);
    
    private final DataSource dataSource;
    
    private static final String SELECT_LAST_POSITION_SQL = 
        "SELECT last_position FROM view_progress WHERE view_name = ?";
    
    private static final String UPDATE_PROGRESS_SQL = """
        INSERT INTO view_progress (view_name, last_position, last_updated_at)
        VALUES (?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (view_name) 
        DO UPDATE SET 
            last_position = EXCLUDED.last_position,
            last_updated_at = CURRENT_TIMESTAMP
        """;
    
    private static final String RECORD_ERROR_SQL = """
        UPDATE view_progress 
        SET error_count = error_count + 1,
            last_error = ?,
            last_error_at = CURRENT_TIMESTAMP,
            status = CASE 
                WHEN error_count + 1 >= ? THEN 'FAILED'
                ELSE status
            END
        WHERE view_name = ?
        """;
    
    private static final String RESET_ERROR_COUNT_SQL = """
        UPDATE view_progress 
        SET error_count = 0,
            last_error = NULL,
            last_error_at = NULL,
            status = 'ACTIVE'
        WHERE view_name = ?
        """;
    
    private static final String SELECT_STATUS_SQL = 
        "SELECT status FROM view_progress WHERE view_name = ?";
    
    private static final String UPDATE_STATUS_SQL = 
        "UPDATE view_progress SET status = ? WHERE view_name = ?";
    
    private static final String AUTO_REGISTER_SQL = """
        INSERT INTO view_progress (view_name, instance_id, status, last_position, last_updated_at)
        VALUES (?, ?, 'ACTIVE', 0, CURRENT_TIMESTAMP)
        ON CONFLICT (view_name) DO NOTHING
        """;
    
    public ViewProgressTracker(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.dataSource = dataSource;
    }
    
    @Override
    public long getLastPosition(String viewName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_LAST_POSITION_SQL)) {
            
            stmt.setString(1, viewName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long position = rs.getLong("last_position");
                    return rs.wasNull() ? 0L : position;
                }
                return 0L;
            }
        } catch (SQLException e) {
            log.debug("View progress not found for {}, returning 0", viewName, e);
            return 0L;
        }
    }
    
    @Override
    public void updateProgress(String viewName, long position) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(UPDATE_PROGRESS_SQL)) {
            
            stmt.setString(1, viewName);
            stmt.setLong(2, position);
            
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                log.warn("No rows updated for progress update: viewName={}", viewName);
            }
        } catch (SQLException e) {
            log.error("Failed to update progress for view: {}", viewName, e);
            throw new RuntimeException("Failed to update progress for view: " + viewName, e);
        }
    }
    
    @Override
    public void recordError(String viewName, String error, int maxErrors) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(RECORD_ERROR_SQL)) {
            
            stmt.setString(1, error);
            stmt.setInt(2, maxErrors);
            stmt.setString(3, viewName);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to record error for view: {}", viewName, e);
            throw new RuntimeException("Failed to record error for view: " + viewName, e);
        }
    }
    
    @Override
    public void resetErrorCount(String viewName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(RESET_ERROR_COUNT_SQL)) {
            
            stmt.setString(1, viewName);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to reset error count for view: {}", viewName, e);
            throw new RuntimeException("Failed to reset error count for view: " + viewName, e);
        }
    }
    
    @Override
    public ProcessorStatus getStatus(String viewName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_STATUS_SQL)) {
            
            stmt.setString(1, viewName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String statusStr = rs.getString("status");
                    if (statusStr != null) {
                        return ProcessorStatus.valueOf(statusStr);
                    }
                }
                return ProcessorStatus.ACTIVE;
            }
        } catch (SQLException e) {
            log.debug("View progress not found for {}, returning ACTIVE", viewName, e);
            return ProcessorStatus.ACTIVE;
        }
    }
    
    @Override
    public void setStatus(String viewName, ProcessorStatus status) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(UPDATE_STATUS_SQL)) {
            
            stmt.setString(1, status.name());
            stmt.setString(2, viewName);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to set status for view: {}", viewName, e);
            throw new RuntimeException("Failed to set status for view: " + viewName, e);
        }
    }
    
    @Override
    public void autoRegister(String viewName, String instanceId) {
        log.trace("[ViewProgressTracker] autoRegister() called for view: {} at {}", viewName, Instant.now());
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(AUTO_REGISTER_SQL)) {
            
            log.trace("[ViewProgressTracker] Executing auto-register SQL for view: {}", viewName);
            stmt.setString(1, viewName);
            stmt.setString(2, instanceId);
            
            stmt.executeUpdate();
            log.debug("[ViewProgressTracker] Successfully auto-registered view: {}", viewName);
        } catch (SQLException e) {
            // Handle missing table gracefully (Flyway might not have run yet)
            // This is a defensive measure to handle timing issues during application startup
            String errorMessage = e.getMessage();
            String sqlState = e.getSQLState();
            
            log.debug("[ViewProgressTracker] SQLException caught for view: {}, message: {}, sqlState: {}", 
                     viewName, errorMessage, sqlState);
            
            // Check for missing table error (PostgreSQL error code 42P01 or message contains "does not exist")
            boolean isMissingTableError = (errorMessage != null && 
                (errorMessage.contains("relation") && errorMessage.contains("does not exist"))) ||
                "42P01".equals(sqlState); // PostgreSQL error code for "undefined_table"
            
            log.debug("[ViewProgressTracker] isMissingTableError={} for view: {}", isMissingTableError, viewName);
            
            if (isMissingTableError) {
                log.info("[ViewProgressTracker] Table view_progress does not exist yet for view: {}. " +
                         "This is expected during application startup before Flyway completes. " +
                         "Table will be created by Flyway, and auto-register will succeed on next call. " +
                         "Error: {}", viewName, errorMessage);
                throw new RuntimeException("Failed to auto-register view: " + viewName + " - table not ready yet", e);
            }
            
            log.error("[ViewProgressTracker] Failed to auto-register view: {} at {}. Error: {}, SQLState: {}", 
                     viewName, Instant.now(), errorMessage, sqlState, e);
            throw new RuntimeException("Failed to auto-register view: " + viewName, e);
        }
    }
}

