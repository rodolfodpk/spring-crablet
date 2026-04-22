package com.crablet.outbox.management;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.outbox.TopicPublisherPair;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class OutboxManagementService implements ProcessorManagementService<TopicPublisherPair> {

    private static final Logger log = LoggerFactory.getLogger(OutboxManagementService.class);

    private final ProcessorManagementService<TopicPublisherPair> delegate;
    private final DataSource dataSource;
    private final ClockProvider clockProvider;

    private static final String SELECT_PROGRESS_SQL = """
        SELECT topic, publisher, status, last_position, last_published_at,
               error_count, last_error, updated_at, leader_instance
        FROM outbox_topic_progress
        WHERE topic = ? AND publisher = ?
        """;

    private static final String SELECT_ALL_PROGRESS_SQL = """
        SELECT topic, publisher, status, last_position, last_published_at,
               error_count, last_error, updated_at, leader_instance
        FROM outbox_topic_progress
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
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (dataSource == null) throw new IllegalArgumentException("dataSource must not be null");
        if (clockProvider == null) throw new IllegalArgumentException("clockProvider must not be null");
        this.delegate = delegate;
        this.dataSource = dataSource;
        this.clockProvider = clockProvider;
    }

    // ========== Delegated Methods ==========

    @Override
    public boolean pause(TopicPublisherPair id) { return delegate.pause(id); }

    @Override
    public boolean resume(TopicPublisherPair id) { return delegate.resume(id); }

    @Override
    public boolean reset(TopicPublisherPair id) { return delegate.reset(id); }

    @Override
    public ProcessorStatus getStatus(TopicPublisherPair id) { return delegate.getStatus(id); }

    @Override
    public Map<TopicPublisherPair, ProcessorStatus> getAllStatuses() { return delegate.getAllStatuses(); }

    @Override
    public @Nullable Long getLag(TopicPublisherPair id) { return delegate.getLag(id); }

    @Override
    public @Nullable BackoffInfo getBackoffInfo(TopicPublisherPair id) { return delegate.getBackoffInfo(id); }

    @Override
    public Map<TopicPublisherPair, BackoffInfo> getAllBackoffInfo() { return delegate.getAllBackoffInfo(); }

    // ========== Outbox-Specific Methods ==========

    /**
     * Get detailed progress for a specific topic-publisher pair.
     *
     * @return details, or {@code null} if not found
     */
    public @Nullable OutboxProgressDetails getProgressDetails(String topic, String publisher) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_PROGRESS_SQL)) {

            stmt.setString(1, topic);
            stmt.setString(2, publisher);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            log.error("Failed to get progress details for outbox topic={} publisher={}", topic, publisher, e);
            throw new RuntimeException("Failed to get outbox progress details", e);
        }
    }

    /**
     * Get detailed progress for all topic-publisher pairs.
     */
    public List<OutboxProgressDetails> getAllProgressDetails() {
        List<OutboxProgressDetails> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_ALL_PROGRESS_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get all outbox progress details", e);
            throw new RuntimeException("Failed to get all outbox progress details", e);
        }
        return result;
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
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : clockProvider.now();

        String leaderInstance = rs.getString("leader_instance");
        if (rs.wasNull()) leaderInstance = null;

        return new OutboxProgressDetails(topic, publisher, status, lastPosition,
                lastPublishedAt, errorCount, lastError, updatedAt, leaderInstance);
    }
}
