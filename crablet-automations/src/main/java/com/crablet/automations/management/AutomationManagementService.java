package com.crablet.automations.management;

import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.progress.ProcessorStatus;
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
 * Management service for automations, extending the generic {@link ProcessorManagementService}
 * with detailed progress monitoring from the {@code automation_progress} table.
 *
 * <p>Applications can inject this as either:
 * <ul>
 *   <li>{@code AutomationManagementService} — to access all methods including detailed progress</li>
 *   <li>{@code ProcessorManagementService<String>} — for generic operations only</li>
 * </ul>
 */
public class AutomationManagementService implements ProcessorManagementService<String> {

    private static final Logger log = LoggerFactory.getLogger(AutomationManagementService.class);

    private final ProcessorManagementService<String> delegate;
    private final DataSource dataSource;

    private static final String SELECT_PROGRESS_SQL = """
        SELECT automation_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM automation_progress
        WHERE automation_name = ?
        """;

    private static final String SELECT_ALL_PROGRESS_SQL = """
        SELECT automation_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM automation_progress
        """;

    public AutomationManagementService(
            ProcessorManagementService<String> delegate,
            DataSource dataSource) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (dataSource == null) throw new IllegalArgumentException("dataSource must not be null");
        this.delegate = delegate;
        this.dataSource = dataSource;
    }

    // ========== Delegated Methods ==========

    @Override
    public boolean pause(String automationName) { return delegate.pause(automationName); }

    @Override
    public boolean resume(String automationName) { return delegate.resume(automationName); }

    @Override
    public boolean reset(String automationName) { return delegate.reset(automationName); }

    @Override
    public ProcessorStatus getStatus(String automationName) { return delegate.getStatus(automationName); }

    @Override
    public Map<String, ProcessorStatus> getAllStatuses() { return delegate.getAllStatuses(); }

    @Override
    public Long getLag(String automationName) { return delegate.getLag(automationName); }

    @Override
    public BackoffInfo getBackoffInfo(String automationName) { return delegate.getBackoffInfo(automationName); }

    @Override
    public Map<String, BackoffInfo> getAllBackoffInfo() { return delegate.getAllBackoffInfo(); }

    // ========== Automation-Specific Methods ==========

    /**
     * Get detailed progress for a specific automation.
     *
     * @return details, or {@code null} if not found
     */
    public AutomationProgressDetails getProgressDetails(String automationName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_PROGRESS_SQL)) {

            stmt.setString(1, automationName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            log.error("Failed to get progress details for automation: {}", automationName, e);
            throw new RuntimeException("Failed to get automation progress details", e);
        }
    }

    /**
     * Get detailed progress for all automations.
     */
    public Map<String, AutomationProgressDetails> getAllProgressDetails() {
        Map<String, AutomationProgressDetails> result = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_ALL_PROGRESS_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                AutomationProgressDetails details = mapRow(rs);
                result.put(details.automationName(), details);
            }
        } catch (SQLException e) {
            log.error("Failed to get all automation progress details", e);
            throw new RuntimeException("Failed to get all automation progress details", e);
        }
        return result;
    }

    private AutomationProgressDetails mapRow(ResultSet rs) throws SQLException {
        String automationName = rs.getString("automation_name");

        String instanceId = rs.getString("instance_id");
        if (rs.wasNull()) instanceId = null;

        String statusStr = rs.getString("status");
        ProcessorStatus status = statusStr != null ? ProcessorStatus.valueOf(statusStr) : ProcessorStatus.ACTIVE;

        long lastPosition = rs.getLong("last_position");
        int errorCount = rs.getInt("error_count");

        String lastError = rs.getString("last_error");
        if (rs.wasNull()) lastError = null;

        Timestamp lastErrorAtTs = rs.getTimestamp("last_error_at");
        Instant lastErrorAt = lastErrorAtTs != null ? lastErrorAtTs.toInstant() : null;

        Timestamp lastUpdatedAtTs = rs.getTimestamp("last_updated_at");
        Instant lastUpdatedAt = lastUpdatedAtTs != null ? lastUpdatedAtTs.toInstant() : Instant.now();

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Instant createdAt = createdAtTs != null ? createdAtTs.toInstant() : Instant.now();

        return new AutomationProgressDetails(
                automationName, instanceId, status, lastPosition,
                errorCount, lastError, lastErrorAt, lastUpdatedAt, createdAt
        );
    }
}
