package com.crablet.eventpoller;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Abstract base class for JDBC-backed {@link EventFetcher} implementations.
 * <p>
 * Provides the shared boilerplate — connection setup, SQL execution, ResultSet mapping,
 * tag parsing, and error handling. Subclasses implement only {@link #buildSqlFilter(Object)}
 * to express their subscription criteria as a SQL WHERE fragment.
 *
 * @param <I> Processor identifier type
 */
public abstract class AbstractJdbcEventFetcher<I> implements EventFetcher<I> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final DataSource readDataSource;

    protected AbstractJdbcEventFetcher(DataSource readDataSource) {
        this.readDataSource = readDataSource;
    }

    @Override
    public final List<StoredEvent> fetchEvents(I processorId, long lastPosition, int batchSize) {
        String sqlFilter = buildSqlFilter(processorId);
        if (sqlFilter == null) {
            log.warn("No SQL filter for processor: {} — skipping fetch", processorId);
            return List.of();
        }

        String sql = """
            SELECT type, tags, data, transaction_id, position, occurred_at,
                   correlation_id, causation_id
            FROM events
            WHERE position > ?
              AND (%s)
            ORDER BY position ASC
            LIMIT ?
            """.formatted(sqlFilter);

        try (Connection connection = readDataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setFetchSize(batchSize);
                stmt.setLong(1, lastPosition);
                stmt.setInt(2, batchSize);

                List<StoredEvent> events = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        events.add(new StoredEvent(
                            rs.getString("type"),
                            parseTagsFromArray(rs.getArray("tags")),
                            rs.getString("data").getBytes(StandardCharsets.UTF_8),
                            rs.getString("transaction_id"),
                            rs.getLong("position"),
                            rs.getTimestamp("occurred_at").toInstant(),
                            rs.getObject("correlation_id", UUID.class),
                            (Long) rs.getObject("causation_id")
                        ));
                    }
                }

                connection.commit();
                log.debug("Fetched {} events for {} after position {}", events.size(), processorId, lastPosition);
                return events;

            } catch (Exception e) {
                connection.rollback();
                log.error("Failed to fetch events for {}: {}", processorId, e.getMessage());
                throw new RuntimeException("Failed to fetch events for " + processorId, e);
            }
        } catch (SQLException e) {
            log.error("Failed to get connection for event fetch: {}", e.getMessage());
            throw new RuntimeException("Failed to get connection for event fetch", e);
        }
    }

    /**
     * Build the SQL WHERE fragment for filtering events for the given processor.
     * Return {@code "TRUE"} to match all events, or {@code null} to skip the fetch entirely.
     *
     * @param processorId the processor identifier (e.g. view name, automation name)
     * @return SQL condition string, or {@code null} to skip
     */
    protected abstract @Nullable String buildSqlFilter(I processorId);

    private List<Tag> parseTagsFromArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        String[] tagStrings = (String[]) array.getArray();
        List<Tag> tags = new ArrayList<>();
        for (String tagStr : tagStrings) {
            int equalsIndex = tagStr.indexOf('=');
            if (equalsIndex > 0) {
                tags.add(new Tag(tagStr.substring(0, equalsIndex), tagStr.substring(equalsIndex + 1)));
            }
        }
        return tags;
    }
}
