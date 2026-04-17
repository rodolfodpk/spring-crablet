package com.crablet.outbox.internal;

import com.crablet.outbox.TopicPublisherPair;
import com.crablet.eventpoller.AbstractJdbcEventFetcher;
import com.crablet.eventpoller.EventSelectionSqlBuilder;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.config.OutboxConfig;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Event fetcher for outbox processors.
 * Fetches events from read replica using topic-based tag filtering.
 */
public class OutboxEventFetcher extends AbstractJdbcEventFetcher<TopicPublisherPair> {

    private final Map<String, TopicConfig> topicConfigs;

    public OutboxEventFetcher(
            DataSource readDataSource,
            OutboxConfig outboxConfig,
            Map<String, TopicConfig> topicConfigs) {
        super(readDataSource);
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
        return EventSelectionSqlBuilder.buildWhereClause(topicConfig);
    }
}
