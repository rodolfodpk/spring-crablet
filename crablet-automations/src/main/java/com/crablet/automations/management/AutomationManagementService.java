package com.crablet.automations.management;

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
 * Management service for automations, extending the generic {@link ProcessorManagementService}
 * with detailed progress monitoring from the {@code crablet_automation_progress} table.
 *
 * <p>Applications can inject this as either:
 * <ul>
 *   <li>{@code AutomationManagementService} — to access all methods including detailed progress</li>
 *   <li>{@code ProcessorManagementService<String>} — for generic operations only</li>
 * </ul>
 */
public class AutomationManagementService extends AbstractProgressManagementService<String> {

    private static final String SELECT_PROGRESS_SQL = """
        SELECT automation_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM crablet_automation_progress
        WHERE automation_name = ?
        """;

    private static final String SELECT_ALL_PROGRESS_SQL = """
        SELECT automation_name, instance_id, status, last_position, error_count,
               last_error, last_error_at, last_updated_at, created_at
        FROM crablet_automation_progress
        """;

    public AutomationManagementService(
            ProcessorManagementService<String> delegate,
            DataSource dataSource) {
        this(delegate, dataSource, ClockProvider.systemDefault());
    }

    public AutomationManagementService(
            ProcessorManagementService<String> delegate,
            DataSource dataSource,
            ClockProvider clockProvider) {
        super(delegate, dataSource, clockProvider);
    }

    // ========== Automation-Specific Methods ==========

    /**
     * Get detailed progress for a specific automation.
     *
     * @return details, or {@code null} if not found
     */
    public @Nullable AutomationProgressDetails getProgressDetails(String automationName) {
        return queryOne(SELECT_PROGRESS_SQL, statement -> statement.setString(1, automationName),
                this::mapRow, "get automation progress details");
    }

    /**
     * Get detailed progress for all automations.
     */
    public Map<String, AutomationProgressDetails> getAllProgressDetails() {
        List<AutomationProgressDetails> details = queryAll(
                SELECT_ALL_PROGRESS_SQL, this::mapRow,
                "get all automation progress details");
        return details.stream().collect(Collectors.toMap(
                AutomationProgressDetails::automationName, Function.identity()));
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
        Instant lastUpdatedAt = lastUpdatedAtTs != null
                ? lastUpdatedAtTs.toInstant() : clockProvider().now();

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Instant createdAt = createdAtTs != null ? createdAtTs.toInstant() : clockProvider().now();

        return new AutomationProgressDetails(
                automationName, instanceId, status, lastPosition,
                errorCount, lastError, lastErrorAt, lastUpdatedAt, createdAt
        );
    }
}
