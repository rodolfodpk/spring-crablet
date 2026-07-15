package com.crablet.outbox.management;

import com.crablet.eventpoller.management.AbstractProgressManagementService;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventstore.ClockProvider;
import com.crablet.outbox.TopicPublisherPair;
import org.jspecify.annotations.Nullable;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Management service for outbox processors, extending the generic
 * {@link ProcessorManagementService} with outbox-specific progress monitoring.
 *
 * <p>Applications can inject this as either:
 * <ul>
 *   <li>{@code OutboxManagementService} — to access detailed progress per topic/publisher</li>
 *   <li>{@code ProcessorManagementService<TopicPublisherPair>} — for generic operations</li>
 * </ul>
 */
public class OutboxManagementService extends AbstractProgressManagementService<TopicPublisherPair> {

    private static final String SELECT_PROGRESS_SQL = """
        SELECT topic, publisher, status, last_position, last_published_at,
               error_count, last_error, updated_at, leader_instance
        FROM crablet_outbox_topic_progress
        WHERE topic = ? AND publisher = ?
        """;

    private static final String SELECT_ALL_PROGRESS_SQL = """
        SELECT topic, publisher, status, last_position, last_published_at,
               error_count, last_error, updated_at, leader_instance
        FROM crablet_outbox_topic_progress
        ORDER BY topic, publisher
        """;

    public OutboxManagementService(
            ProcessorManagementService<TopicPublisherPair> delegate,
            DataSource dataSource) {
        this(delegate, dataSource, ClockProvider.systemDefault());
    }

    public OutboxManagementService(
            ProcessorManagementService<TopicPublisherPair> delegate,
            DataSource dataSource,
            ClockProvider clockProvider) {
        super(delegate, dataSource, clockProvider);
    }

    // ========== Outbox-Specific Methods ==========

    /**
     * Get detailed progress for a specific topic-publisher pair.
     *
     * @return details, or {@code null} if not found
     */
    public @Nullable OutboxProgressDetails getProgressDetails(String topic, String publisher) {
        return queryOne(SELECT_PROGRESS_SQL, statement -> {
            statement.setString(1, topic);
            statement.setString(2, publisher);
        }, this::mapRow, "get outbox progress details");
    }

    /**
     * Get detailed progress for all topic-publisher pairs.
     */
    public List<OutboxProgressDetails> getAllProgressDetails() {
        return queryAll(
                SELECT_ALL_PROGRESS_SQL, this::mapRow, "get all outbox progress details");
    }

    private OutboxProgressDetails mapRow(ResultSet rs) throws SQLException {
        String topic = rs.getString("topic");
        String publisher = rs.getString("publisher");

        String statusStr = rs.getString("status");
        ProcessorStatus status = statusStr != null ? ProcessorStatus.valueOf(statusStr) : ProcessorStatus.ACTIVE;

        long lastPosition = rs.getLong("last_position");
        int errorCount = rs.getInt("error_count");

        String lastError = rs.getString("last_error");
        if (rs.wasNull()) lastError = null;

        Timestamp lastPublishedAtTs = rs.getTimestamp("last_published_at");
        Instant lastPublishedAt = lastPublishedAtTs != null ? lastPublishedAtTs.toInstant() : null;

        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : clockProvider().now();

        String leaderInstance = rs.getString("leader_instance");
        if (rs.wasNull()) leaderInstance = null;

        return new OutboxProgressDetails(topic, publisher, status, lastPosition,
                lastPublishedAt, errorCount, lastError, updatedAt, leaderInstance);
    }
}
