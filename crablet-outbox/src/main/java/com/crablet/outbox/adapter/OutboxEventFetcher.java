package com.crablet.outbox.adapter;

import com.crablet.eventpoller.AbstractJdbcEventFetcher;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.config.OutboxConfig;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event fetcher for outbox processors.
 * Fetches events from read replica using topic-based tag filtering.
 */
public class OutboxEventFetcher extends AbstractJdbcEventFetcher<TopicPublisherPair> {

    private final OutboxConfig outboxConfig;
    private final Map<String, TopicConfig> topicConfigs;

    public OutboxEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            OutboxConfig outboxConfig,
            Map<String, TopicConfig> topicConfigs) {
        super(readDataSource);
        this.outboxConfig = outboxConfig;
        this.topicConfigs = topicConfigs;
    }

    @Override
    protected String buildSqlFilter(TopicPublisherPair processorId) {
        String topicName = processorId.topic();
        TopicConfig topicConfig = topicConfigs.get(topicName);

        if (topicConfig == null) {
            log.warn("Topic '{}' not found for processor {}", topicName, processorId);
            return null;
        }

        Set<String> requiredTags = topicConfig.getRequiredTags();
        Set<String> anyOfTags = topicConfig.getAnyOfTags();
        Map<String, String> exactTags = topicConfig.getExactTags();

        if (requiredTags.isEmpty() && anyOfTags.isEmpty() && exactTags.isEmpty()) {
            return "TRUE";
        }

        List<String> conditions = new ArrayList<>();

        for (String tag : requiredTags) {
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tag + "=%')");
        }

        if (!anyOfTags.isEmpty()) {
            String anyOfCondition = anyOfTags.stream()
                .map(tag -> "t LIKE '" + tag + "=%'")
                .collect(Collectors.joining(" OR "));
            conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");
        }

        for (var entry : exactTags.entrySet()) {
            conditions.add("'" + entry.getKey() + "=" + entry.getValue() + "' = ANY(tags)");
        }

        return String.join(" AND ", conditions);
    }
}
