package com.crablet.views.adapter;

import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.crablet.views.config.ViewSubscriptionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

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
 * Event fetcher for view projections.
 * Fetches events from read replica using event type and tag filtering based on subscription config.
 */
public class ViewEventFetcher implements EventFetcher<String> {
    
    private static final Logger log = LoggerFactory.getLogger(ViewEventFetcher.class);
    
    private final DataSource readDataSource;
    private final Map<String, ViewSubscriptionConfig> subscriptions;
    
    public ViewEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            Map<String, ViewSubscriptionConfig> subscriptions) {
        this.readDataSource = readDataSource;
        this.subscriptions = subscriptions;
    }
    
    @Override
    public List<StoredEvent> fetchEvents(String viewName, long lastPosition, int batchSize) {
        ViewSubscriptionConfig subscription = subscriptions.get(viewName);
        
        if (subscription == null) {
            log.warn("Subscription not found for view: {}", viewName);
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
                log.debug("Fetched {} events for view {} after position {}", events.size(), viewName, lastPosition);
                return events;
                
            } catch (Exception e) {
                connection.rollback();
                log.error("Failed to fetch events for view {}: {}", viewName, e.getMessage());
                throw new RuntimeException("Failed to fetch events for view: " + viewName, e);
            }
        } catch (SQLException e) {
            log.error("Failed to get connection for event fetch: {}", e.getMessage());
            throw new RuntimeException("Failed to get connection for event fetch", e);
        }
    }
    
    /**
     * Generate SQL filter for subscription (event types + tags).
     */
    private String buildSqlFilter(ViewSubscriptionConfig subscription) {
        Set<String> eventTypes = subscription.getEventTypes();
        Set<String> requiredTags = subscription.getRequiredTags();
        Set<String> anyOfTags = subscription.getAnyOfTags();
        
        List<String> conditions = new ArrayList<>();
        
        // Event type filter
        if (!eventTypes.isEmpty()) {
            String typeCondition = eventTypes.stream()
                .map(type -> "'" + type + "'")
                .collect(Collectors.joining(", "));
            conditions.add("type IN (" + typeCondition + ")");
        }
        
        // Required tags: ALL must be present
        for (String tagKey : requiredTags) {
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tagKey + ":%')");
        }
        
        // AnyOf tags: at least ONE must be present
        if (!anyOfTags.isEmpty()) {
            String anyOfCondition = anyOfTags.stream()
                .map(tagKey -> "t LIKE '" + tagKey + ":%'")
                .collect(Collectors.joining(" OR "));
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");
        }
        
        if (conditions.isEmpty()) {
            return "TRUE"; // Match all if no filters
        }
        
        return String.join(" AND ", conditions);
    }
    
    private List<Tag> parseTagsFromArray(java.sql.Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        
        String[] tagStrings = (String[]) array.getArray();
        List<Tag> tags = new ArrayList<>();
        
        for (String tagStr : tagStrings) {
            // Format: "key:value"
            int colonIndex = tagStr.indexOf(':');
            if (colonIndex > 0) {
                String key = tagStr.substring(0, colonIndex);
                String value = tagStr.substring(colonIndex + 1);
                tags.add(new Tag(key, value));
            }
        }
        
        return tags;
    }
}

