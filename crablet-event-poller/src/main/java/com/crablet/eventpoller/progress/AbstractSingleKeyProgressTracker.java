package com.crablet.eventpoller.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Abstract base class for JDBC-backed {@link ProgressTracker} implementations with a single-column primary key.
 * <p>
 * Provides all SQL logic parameterized by table name and identity column name.
 * Subclasses supply the table and column names via the constructor — no other code is required.
 * <p>
 * Suitable for trackers whose progress table has a single {@code VARCHAR} primary key
 * (e.g. {@code view_name}, {@code automation_name}). Trackers with composite keys
 * (e.g. outbox's {@code (topic, publisher)}) should implement {@link ProgressTracker} directly.
 */
public abstract class AbstractSingleKeyProgressTracker implements ProgressTracker<String> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final DataSource dataSource;
    private final String tableName;
    private final String idColumn;

    protected AbstractSingleKeyProgressTracker(DataSource dataSource, String tableName, String idColumn) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource must not be null");
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.idColumn = idColumn;
    }

    @Override
    public long getLastPosition(String processorId) {
        String sql = "SELECT last_position FROM " + tableName + " WHERE " + idColumn + " = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, processorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long pos = rs.getLong("last_position");
                    return rs.wasNull() ? 0L : pos;
                }
                return 0L;
            }
        } catch (SQLException e) {
            log.debug("Progress not found for {} in {}, returning 0", processorId, tableName, e);
            return 0L;
        }
    }

    @Override
    public void updateProgress(String processorId, long position) {
        String sql = """
            INSERT INTO %s (%s, last_position, last_updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (%s)
            DO UPDATE SET
                last_position = EXCLUDED.last_position,
                last_updated_at = CURRENT_TIMESTAMP
            """.formatted(tableName, idColumn, idColumn);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, processorId);
            stmt.setLong(2, position);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update progress for {} in {}", processorId, tableName, e);
            throw new RuntimeException("Failed to update progress for " + processorId, e);
        }
    }

    @Override
    public void recordError(String processorId, String error, int maxErrors) {
        String sql = """
            UPDATE %s
            SET error_count = error_count + 1,
                last_error = ?,
                last_error_at = CURRENT_TIMESTAMP,
                status = CASE
                    WHEN error_count + 1 >= ? THEN 'FAILED'
                    ELSE status
                END
            WHERE %s = ?
            """.formatted(tableName, idColumn);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, error);
            stmt.setInt(2, maxErrors);
            stmt.setString(3, processorId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to record error for {} in {}", processorId, tableName, e);
            throw new RuntimeException("Failed to record error for " + processorId, e);
        }
    }

    @Override
    public void resetErrorCount(String processorId) {
        String sql = """
            UPDATE %s
            SET error_count = 0,
                last_error = NULL,
                last_error_at = NULL,
                status = 'ACTIVE'
            WHERE %s = ?
            """.formatted(tableName, idColumn);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, processorId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to reset error count for {} in {}", processorId, tableName, e);
            throw new RuntimeException("Failed to reset error count for " + processorId, e);
        }
    }

    @Override
    public ProcessorStatus getStatus(String processorId) {
        String sql = "SELECT status FROM " + tableName + " WHERE " + idColumn + " = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, processorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String statusStr = rs.getString("status");
                    if (statusStr != null) return ProcessorStatus.valueOf(statusStr);
                }
                return ProcessorStatus.ACTIVE;
            }
        } catch (SQLException e) {
            log.debug("Progress not found for {} in {}, returning ACTIVE", processorId, tableName, e);
            return ProcessorStatus.ACTIVE;
        }
    }

    @Override
    public void setStatus(String processorId, ProcessorStatus status) {
        String sql = "UPDATE " + tableName + " SET status = ? WHERE " + idColumn + " = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setString(2, processorId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to set status for {} in {}", processorId, tableName, e);
            throw new RuntimeException("Failed to set status for " + processorId, e);
        }
    }

    @Override
    public void autoRegister(String processorId, String instanceId) {
        String sql = """
            INSERT INTO %s (%s, instance_id, status, last_position, last_updated_at)
            VALUES (?, ?, 'ACTIVE', 0, CURRENT_TIMESTAMP)
            ON CONFLICT (%s) DO NOTHING
            """.formatted(tableName, idColumn, idColumn);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, processorId);
            stmt.setString(2, instanceId);
            stmt.executeUpdate();
            log.debug("Auto-registered {} in {}", processorId, tableName);
        } catch (SQLException e) {
            String sqlState = e.getSQLState();
            if ("42P01".equals(sqlState) ||
                    (e.getMessage() != null && e.getMessage().contains("does not exist"))) {
                log.info("Table {} not ready yet for {}. Flyway will create it.", tableName, processorId);
                throw new RuntimeException("Failed to auto-register " + processorId + " - table not ready yet", e);
            }
            log.error("Failed to auto-register {} in {} at {}", processorId, tableName, Instant.now(), e);
            throw new RuntimeException("Failed to auto-register " + processorId, e);
        }
    }
}
