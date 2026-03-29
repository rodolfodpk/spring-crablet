package com.crablet.automations.adapter;

import com.crablet.automations.AutomationSubscription;
import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fetches events for automation processors using event type and tag filtering.
 */
public class AutomationEventFetcher implements EventFetcher<String> {

    private static final Logger log = LoggerFactory.getLogger(AutomationEventFetcher.class);

    private final DataSource readDataSource;
    private final Map<String, AutomationSubscription> subscriptions;

    public AutomationEventFetcher(DataSource readDataSource, Map<String, AutomationSubscription> subscriptions) {
        this.readDataSource = readDataSource;
        this.subscriptions = subscriptions;
    }

    @Override
    public List<StoredEvent> fetchEvents(String automationName, long lastPosition, int batchSize) {
        AutomationSubscription subscription = subscriptions.get(automationName);
        if (subscription == null) {
            log.warn("Subscription not found for automation: {} (available: {})", automationName, subscriptions.keySet());
            return List.of();
        }

        String sqlFilter = buildSqlFilter(subscription);
        String sql = """
            SELECT type, tags, data, transaction_id, position, occurred_at
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
                            rs.getString("data").getBytes(),
                            rs.getString("transaction_id"),
                            rs.getLong("position"),
                            rs.getTimestamp("occurred_at").toInstant()
                        ));
                    }
                }

                connection.commit();
                log.debug("Fetched {} events for automation {} after position {}", events.size(), automationName, lastPosition);
                return events;

            } catch (Exception e) {
                connection.rollback();
                throw new RuntimeException("Failed to fetch events for automation: " + automationName, e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get connection for automation event fetch", e);
        }
    }

    private String buildSqlFilter(AutomationSubscription subscription) {
        Set<String> eventTypes = subscription.getEventTypes();
        Set<String> requiredTags = subscription.getRequiredTags();
        Set<String> anyOfTags = subscription.getAnyOfTags();

        List<String> conditions = new ArrayList<>();

        if (!eventTypes.isEmpty()) {
            String typeList = eventTypes.stream()
                .map(t -> "'" + t + "'")
                .collect(Collectors.joining(", "));
            conditions.add("type IN (" + typeList + ")");
        }

        for (String tagKey : requiredTags) {
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tagKey + "=%')");
        }

        if (!anyOfTags.isEmpty()) {
            String anyOfCondition = anyOfTags.stream()
                .map(tagKey -> "t LIKE '" + tagKey + "=%'")
                .collect(Collectors.joining(" OR "));
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");
        }

        return conditions.isEmpty() ? "TRUE" : String.join(" AND ", conditions);
    }

    private List<Tag> parseTagsFromArray(java.sql.Array array) throws SQLException {
        if (array == null) return List.of();
        String[] tagStrings = (String[]) array.getArray();
        List<Tag> tags = new ArrayList<>();
        for (String tagStr : tagStrings) {
            int idx = tagStr.indexOf('=');
            if (idx > 0) {
                tags.add(new Tag(tagStr.substring(0, idx), tagStr.substring(idx + 1)));
            }
        }
        return tags;
    }
}
