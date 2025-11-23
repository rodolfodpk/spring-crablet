package com.crablet.outbox.adapter;

import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.config.OutboxConfig;
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

/**
 * Event fetcher for outbox processors.
 * Fetches events from read replica using topic-based tag filtering.
 */
public class OutboxEventFetcher implements EventFetcher<TopicPublisherPair> {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxEventFetcher.class);
    
    private final DataSource readDataSource;
    private final OutboxConfig outboxConfig;
    private final Map<String, TopicConfig> topicConfigs;
    
    public OutboxEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            OutboxConfig outboxConfig,
            Map<String, TopicConfig> topicConfigs) {
        this.readDataSource = readDataSource;
        this.outboxConfig = outboxConfig;
        this.topicConfigs = topicConfigs;
    }
    
    @Override
    public List<StoredEvent> fetchEvents(TopicPublisherPair processorId, long lastPosition, int batchSize) {
        String topicName = processorId.topic();
        TopicConfig topicConfig = topicConfigs.get(topicName);
        
        if (topicConfig == null) {
            log.warn("Topic '{}' not found for processor {}", topicName, processorId);
            return List.of();
        }
        
        String sqlFilter = buildSqlFilterForTopic(topicConfig);
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
                stmt.setFetchSize(outboxConfig.getFetchSize());
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
     * Generate SQL filter for topic tag matching.
     * Uses PostgreSQL array operators for efficiency.
     */
    private String buildSqlFilterForTopic(TopicConfig topicConfig) {
        Set<String> requiredTags = topicConfig.getRequiredTags();
        Set<String> anyOfTags = topicConfig.getAnyOfTags();
        Map<String, String> exactTags = topicConfig.getExactTags();
        
        if (requiredTags.isEmpty() && anyOfTags.isEmpty() && exactTags.isEmpty()) {
            return "TRUE"; // Match all
        }
        
        List<String> conditions = new ArrayList<>();
        
        // Required tags: ALL must be present
        for (String tag : requiredTags) {
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tag + ":%')");
        }
        
        // AnyOf tags: at least ONE must be present
        if (!anyOfTags.isEmpty()) {
            String anyOfCondition = anyOfTags.stream()
                .map(tag -> "t LIKE '" + tag + ":%'")
                .collect(java.util.stream.Collectors.joining(" OR "));
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");
        }
        
        // Exact matches
        for (var entry : exactTags.entrySet()) {
            conditions.add("'" + entry.getKey() + ":" + entry.getValue() + "' = ANY(tags)");
        }
        
        return String.join(" AND ", conditions);
    }
    
    private List<com.crablet.eventstore.store.Tag> parseTagsFromArray(java.sql.Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        
        String[] tagStrings = (String[]) array.getArray();
        List<com.crablet.eventstore.store.Tag> tags = new ArrayList<>();
        
        for (String tagStr : tagStrings) {
            // Format: "key:value"
            int colonIndex = tagStr.indexOf(':');
            if (colonIndex > 0) {
                String key = tagStr.substring(0, colonIndex);
                String value = tagStr.substring(colonIndex + 1);
                tags.add(new com.crablet.eventstore.store.Tag(key, value));
            }
        }
        
        return tags;
    }
}

