package com.crablet.outbox.adapter;

import com.crablet.eventprocessor.processor.ProcessorConfig;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that creates ProcessorConfig instances from OutboxConfig and TopicConfig.
 * Creates one ProcessorConfig per (topic, publisher) pair.
 */
public class OutboxProcessorConfig implements ProcessorConfig<TopicPublisherPair> {
    
    private final TopicPublisherPair processorId;
    private final OutboxConfig outboxConfig;
    private final TopicConfig topicConfig;
    private final TopicConfigurationProperties topicConfigProperties;
    
    public OutboxProcessorConfig(
            TopicPublisherPair processorId,
            OutboxConfig outboxConfig,
            TopicConfig topicConfig,
            TopicConfigurationProperties topicConfigProperties) {
        this.processorId = processorId;
        this.outboxConfig = outboxConfig;
        this.topicConfig = topicConfig;
        this.topicConfigProperties = topicConfigProperties;
    }
    
    @Override
    public TopicPublisherPair getProcessorId() {
        return processorId;
    }
    
    @Override
    public long getPollingIntervalMs() {
        // Check for per-publisher override
        String topicName = processorId.topic();
        String publisherName = processorId.publisher();
        
        TopicConfigurationProperties.TopicProperties topicProps = 
            topicConfigProperties.getTopics().get(topicName);
        
        if (topicProps != null && topicProps.getPublisherConfigs() != null) {
            for (TopicConfigurationProperties.PublisherProperties pubConfig : topicProps.getPublisherConfigs()) {
                if (publisherName.equals(pubConfig.getName()) && pubConfig.getPollingIntervalMs() != null) {
                    return pubConfig.getPollingIntervalMs();
                }
            }
        }
        
        return outboxConfig.getPollingIntervalMs(); // Global fallback
    }
    
    @Override
    public int getBatchSize() {
        return outboxConfig.getBatchSize();
    }
    
    @Override
    public boolean isBackoffEnabled() {
        return outboxConfig.isBackoffEnabled();
    }
    
    @Override
    public int getBackoffThreshold() {
        return outboxConfig.getBackoffThreshold();
    }
    
    @Override
    public int getBackoffMultiplier() {
        return outboxConfig.getBackoffMultiplier();
    }
    
    @Override
    public int getBackoffMaxSeconds() {
        return outboxConfig.getBackoffMaxSeconds();
    }
    
    @Override
    public boolean isEnabled() {
        return outboxConfig.isEnabled();
    }
    
    /**
     * Create a map of all (topic, publisher) pairs to their ProcessorConfig instances.
     */
    public static Map<TopicPublisherPair, OutboxProcessorConfig> createConfigMap(
            OutboxConfig outboxConfig,
            TopicConfigurationProperties topicConfigProperties) {
        
        Map<TopicPublisherPair, OutboxProcessorConfig> configs = new HashMap<>();
        
        Map<String, TopicConfig> topics = outboxConfig.getTopics();
        for (var topicEntry : topics.entrySet()) {
            String topicName = topicEntry.getKey();
            TopicConfig topicConfig = topicEntry.getValue();
            
            for (String publisherName : topicConfig.getPublishers()) {
                TopicPublisherPair pair = new TopicPublisherPair(topicName, publisherName);
                OutboxProcessorConfig config = new OutboxProcessorConfig(
                    pair, outboxConfig, topicConfig, topicConfigProperties);
                configs.put(pair, config);
            }
        }
        
        return configs;
    }
}

