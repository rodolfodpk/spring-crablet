package com.crablet.views.service;

import com.crablet.eventpoller.management.AbstractProgressManagementService;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventstore.ClockProvider;
import org.jspecify.annotations.Nullable;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unified service for view management that extends ProcessorManagementService
 * with detailed progress monitoring capabilities.
 * 
 * <p>This service:
 * <ul>
 *   <li>Implements {@link ProcessorManagementService} for operations (pause, resume, reset, status, lag)</li>
 *   <li>Adds detailed progress monitoring via {@link #getProgressDetails(String)}</li>
 *   <li>Provides comprehensive view information from the crablet_view_progress table</li>
 * </ul>
 * 
 * <p>Applications can inject this service as either:
 * <ul>
 *   <li>{@code ViewManagementService} - to access all methods including detailed progress</li>
 *   <li>{@code ProcessorManagementService<String>} - for backward compatibility</li>
 * </ul>
 */
public class ViewManagementService extends AbstractProgressManagementService<String> {

    private static final String SELECT_PROGRESS_SQL = """
        SELECT view_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM crablet_view_progress
        WHERE view_name = ?
        """;
    
    private static final String SELECT_ALL_PROGRESS_SQL = """
        SELECT view_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM crablet_view_progress
        """;
    
    public ViewManagementService(
            ProcessorManagementService<String> delegate,
            DataSource dataSource) {
        this(delegate, dataSource, ClockProvider.systemDefault());
    }

    public ViewManagementService(
            ProcessorManagementService<String> delegate,
            DataSource dataSource,
            ClockProvider clockProvider) {
        super(delegate, dataSource, clockProvider);
    }

    // ========== Detailed Progress Monitoring ==========
    
    /**
     * Get detailed progress information for a specific view.
     * 
     * @param viewName View name
     * @return ViewProgressDetails or null if view not found
     */
    public @Nullable ViewProgressDetails getProgressDetails(String viewName) {
        return queryOne(SELECT_PROGRESS_SQL, statement -> statement.setString(1, viewName),
                this::mapRowToDetails, "get progress details for view: " + viewName);
    }
    
    /**
     * Get detailed progress information for all views.
     * 
     * @return Map of view name to ViewProgressDetails
     */
    public Map<String, ViewProgressDetails> getAllProgressDetails() {
        List<ViewProgressDetails> details = queryAll(
                SELECT_ALL_PROGRESS_SQL, this::mapRowToDetails, "get all progress details");
        return details.stream().collect(Collectors.toMap(
                ViewProgressDetails::viewName, Function.identity()));
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
            : clockProvider().now(); // Fallback, though this should never be null
        
        Timestamp createdAtTimestamp = rs.getTimestamp("created_at");
        Instant createdAt = createdAtTimestamp != null 
            ? createdAtTimestamp.toInstant() 
            : clockProvider().now(); // Fallback, though this should never be null
        
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
