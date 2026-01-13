package com.crablet.views.service;

import com.crablet.eventprocessor.management.ProcessorManagementService;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified service for view management that extends ProcessorManagementService
 * with detailed progress monitoring capabilities.
 * 
 * <p>This service:
 * <ul>
 *   <li>Implements {@link ProcessorManagementService} for operations (pause, resume, reset, status, lag)</li>
 *   <li>Adds detailed progress monitoring via {@link #getProgressDetails(String)}</li>
 *   <li>Provides comprehensive view information from the view_progress table</li>
 * </ul>
 * 
 * <p>Applications can inject this service as either:
 * <ul>
 *   <li>{@code ViewManagementService} - to access all methods including detailed progress</li>
 *   <li>{@code ProcessorManagementService<String>} - for backward compatibility</li>
 * </ul>
 */
public class ViewManagementService implements ProcessorManagementService<String> {
    
    private static final Logger log = LoggerFactory.getLogger(ViewManagementService.class);
    
    private final ProcessorManagementService<String> delegate;
    private final DataSource dataSource;
    
    private static final String SELECT_PROGRESS_SQL = """
        SELECT view_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM view_progress
        WHERE view_name = ?
        """;
    
    private static final String SELECT_ALL_PROGRESS_SQL = """
        SELECT view_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM view_progress
        """;
    
    public ViewManagementService(
            ProcessorManagementService<String> delegate,
            DataSource dataSource) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.delegate = delegate;
        this.dataSource = dataSource;
    }
    
    // ========== Delegated Methods (ProcessorManagementService interface) ==========
    
    @Override
    public boolean pause(String viewName) {
        return delegate.pause(viewName);
    }
    
    @Override
    public boolean resume(String viewName) {
        return delegate.resume(viewName);
    }
    
    @Override
    public boolean reset(String viewName) {
        return delegate.reset(viewName);
    }
    
    @Override
    public ProcessorStatus getStatus(String viewName) {
        return delegate.getStatus(viewName);
    }
    
    @Override
    public Map<String, ProcessorStatus> getAllStatuses() {
        return delegate.getAllStatuses();
    }
    
    @Override
    public Long getLag(String viewName) {
        return delegate.getLag(viewName);
    }
    
    @Override
    public BackoffInfo getBackoffInfo(String viewName) {
        return delegate.getBackoffInfo(viewName);
    }
    
    @Override
    public Map<String, BackoffInfo> getAllBackoffInfo() {
        return delegate.getAllBackoffInfo();
    }
    
    // ========== New Methods (Detailed Progress Monitoring) ==========
    
    /**
     * Get detailed progress information for a specific view.
     * 
     * @param viewName View name
     * @return ViewProgressDetails or null if view not found
     */
    public ViewProgressDetails getProgressDetails(String viewName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_PROGRESS_SQL)) {
            
            stmt.setString(1, viewName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDetails(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            log.error("Failed to get progress details for view: {}", viewName, e);
            throw new RuntimeException("Failed to get progress details for view: " + viewName, e);
        }
    }
    
    /**
     * Get detailed progress information for all views.
     * 
     * @return Map of view name to ViewProgressDetails
     */
    public Map<String, ViewProgressDetails> getAllProgressDetails() {
        Map<String, ViewProgressDetails> result = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_ALL_PROGRESS_SQL);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                ViewProgressDetails details = mapRowToDetails(rs);
                result.put(details.viewName(), details);
            }
            
            return result;
        } catch (SQLException e) {
            log.error("Failed to get all progress details", e);
            throw new RuntimeException("Failed to get all progress details", e);
        }
    }
    
    /**
     * Map a ResultSet row to ViewProgressDetails.
     */
    private ViewProgressDetails mapRowToDetails(ResultSet rs) throws SQLException {
        String viewName = rs.getString("view_name");
        String instanceId = rs.getString("instance_id");
        if (rs.wasNull()) {
            instanceId = null;
        }
        
        String statusStr = rs.getString("status");
        ProcessorStatus status = statusStr != null 
            ? ProcessorStatus.valueOf(statusStr) 
            : ProcessorStatus.ACTIVE;
        
        long lastPosition = rs.getLong("last_position");
        int errorCount = rs.getInt("error_count");
        
        String lastError = rs.getString("last_error");
        if (rs.wasNull()) {
            lastError = null;
        }
        
        Timestamp lastErrorAtTimestamp = rs.getTimestamp("last_error_at");
        Instant lastErrorAt = lastErrorAtTimestamp != null 
            ? lastErrorAtTimestamp.toInstant() 
            : null;
        
        Timestamp lastUpdatedAtTimestamp = rs.getTimestamp("last_updated_at");
        Instant lastUpdatedAt = lastUpdatedAtTimestamp != null 
            ? lastUpdatedAtTimestamp.toInstant() 
            : Instant.now(); // Fallback, though this should never be null
        
        Timestamp createdAtTimestamp = rs.getTimestamp("created_at");
        Instant createdAt = createdAtTimestamp != null 
            ? createdAtTimestamp.toInstant() 
            : Instant.now(); // Fallback, though this should never be null
        
        return new ViewProgressDetails(
            viewName,
            instanceId,
            status,
            lastPosition,
            errorCount,
            lastError,
            lastErrorAt,
            lastUpdatedAt,
            createdAt
        );
    }
}
