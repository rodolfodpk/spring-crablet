package com.crablet.views.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * Seeds {@code view_progress} rows for view management integration tests.
 */
public final class ViewProgressFixtures {

    private ViewProgressFixtures() {}

    public static void insertWithErrorColumns(
            JdbcTemplate jdbc,
            String viewName,
            String instanceId,
            String status,
            long lastPosition,
            int errorCount,
            String lastError,
            Timestamp lastErrorAt,
            Timestamp lastUpdatedAt,
            Timestamp createdAt) {
        jdbc.update(
                """
                INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count,
                                          last_error, last_error_at, last_updated_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                viewName,
                instanceId,
                status,
                lastPosition,
                errorCount,
                lastError,
                lastErrorAt,
                lastUpdatedAt,
                createdAt);
    }

    public static void insertWithoutErrorColumns(
            JdbcTemplate jdbc,
            String viewName,
            String instanceId,
            String status,
            long lastPosition,
            int errorCount,
            Timestamp lastUpdatedAt,
            Timestamp createdAt) {
        jdbc.update(
                """
                INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count,
                                          last_updated_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                viewName,
                instanceId,
                status,
                lastPosition,
                errorCount,
                lastUpdatedAt,
                createdAt);
    }
}
