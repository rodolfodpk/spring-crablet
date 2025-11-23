package com.crablet.views.adapter;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventprocessor.progress.ProgressTracker;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adapter that implements ProgressTracker<String> for view projections.
 * Uses the view_progress table to track processing progress per view.
 */
public class ViewProgressTracker implements ProgressTracker<String> {
    
    private final JdbcTemplate jdbcTemplate;
    
    public ViewProgressTracker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public long getLastPosition(String viewName) {
        Long position = jdbcTemplate.queryForObject(
            "SELECT last_position FROM view_progress WHERE view_name = ?",
            Long.class,
            viewName
        );
        return position != null ? position : 0L;
    }
    
    @Override
    public void updateProgress(String viewName, long position) {
        jdbcTemplate.update(
            """
            INSERT INTO view_progress (view_name, last_position, last_updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (view_name) 
            DO UPDATE SET 
                last_position = EXCLUDED.last_position,
                last_updated_at = CURRENT_TIMESTAMP
            """,
            viewName, position
        );
    }
    
    @Override
    public void recordError(String viewName, String error, int maxErrors) {
        jdbcTemplate.update(
            """
            UPDATE view_progress 
            SET error_count = error_count + 1,
                last_error = ?,
                last_error_at = CURRENT_TIMESTAMP,
                status = CASE 
                    WHEN error_count + 1 >= ? THEN 'FAILED'
                    ELSE status
                END
            WHERE view_name = ?
            """,
            error, maxErrors, viewName
        );
    }
    
    @Override
    public void resetErrorCount(String viewName) {
        jdbcTemplate.update(
            """
            UPDATE view_progress 
            SET error_count = 0,
                last_error = NULL,
                last_error_at = NULL,
                status = 'ACTIVE'
            WHERE view_name = ?
            """,
            viewName
        );
    }
    
    @Override
    public ProcessorStatus getStatus(String viewName) {
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM view_progress WHERE view_name = ?",
            String.class,
            viewName
        );
        return status != null ? ProcessorStatus.valueOf(status) : ProcessorStatus.ACTIVE;
    }
    
    @Override
    public void setStatus(String viewName, ProcessorStatus status) {
        jdbcTemplate.update(
            "UPDATE view_progress SET status = ? WHERE view_name = ?",
            status.name(), viewName
        );
    }
    
    @Override
    public void autoRegister(String viewName, String instanceId) {
        jdbcTemplate.update(
            """
            INSERT INTO view_progress (view_name, instance_id, status, last_position, last_updated_at)
            VALUES (?, ?, 'ACTIVE', 0, CURRENT_TIMESTAMP)
            ON CONFLICT (view_name) DO NOTHING
            """,
            viewName, instanceId
        );
    }
}

