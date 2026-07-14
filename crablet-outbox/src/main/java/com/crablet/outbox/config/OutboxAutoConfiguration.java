package com.crablet.outbox.config;

import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventProcessorFactory;
import com.crablet.eventpoller.ProcessorSpec;
import com.crablet.eventpoller.EventSelection;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.config.EventPollerAutoConfiguration;
import com.crablet.eventpoller.config.EventPollerConfig;
import com.crablet.eventpoller.sharedfetch.ModuleScanProgressRepository;
import com.crablet.eventpoller.sharedfetch.ProcessorScanProgressRepository;
import com.crablet.eventpoller.sharedfetch.SharedFetchModuleProcessor;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSource;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSourceFactory;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.Internal;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.internal.OutboxEventFetcher;
import com.crablet.outbox.internal.OutboxEventHandler;
import com.crablet.outbox.internal.OutboxProcessorConfig;
import com.crablet.outbox.internal.OutboxProgressTracker;
import com.crablet.outbox.management.OutboxManagementService;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.publishing.OutboxPublishingServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-configuration for outbox using the generic event processor.
 * 
 * <p>This configuration creates all necessary beans to use the generic
 * EventProcessor with outbox-specific adapters.
 * 
 * <p>Enabled when {@code crablet.outbox.enabled=true}.
 */
@Internal
@AutoConfiguration(after = EventPollerAutoConfiguration.class)
@ConditionalOnProperty(name = "crablet.outbox.enabled", havingValue = "true", matchIfMissing = false)
public class OutboxAutoConfiguration {
    
    // Advisory lock key for outbox (different from views and automations)
    private static final long OUTBOX_LOCK_KEY = 4856221667890123456L;

    /**
     * Create LeaderElector bean for outbox — exposed so tests can inject it directly.
     */
    @Bean
    public LeaderElector outboxLeaderElector(
            InstanceIdProvider instanceIdProvider,
            WriteDataSource writeDataSource,
            ApplicationEventPublisher eventPublisher) {
        return EventProcessorFactory.createLeaderElector(
                writeDataSource, "outbox", instanceIdProvider.getInstanceId(),
                OUTBOX_LOCK_KEY, eventPublisher);
    }

    /**
     * Create OutboxProgressTracker bean.
     */
    @Bean
    public ProgressTracker<TopicPublisherPair> outboxProgressTracker(
            WriteDataSource writeDataSource) {
        return new OutboxProgressTracker(writeDataSource.dataSource());
    }
    
    /**
     * Create OutboxEventFetcher bean.
     */
    @Bean
    public EventFetcher<TopicPublisherPair> outboxEventFetcher(
            ReadDataSource readDataSource,
            Map<String, TopicConfig> topicConfigs) {
        return new OutboxEventFetcher(readDataSource.dataSource(), topicConfigs);
    }
    
    /**
     * Create OutboxPublishingService bean.
     * This is still needed for the publishing logic.
     * 
     * Note: OutboxPublishingServiceImpl still uses JdbcTemplate internally for some operations.
     * This is acceptable as it's a legacy class that will be refactored separately.
     */
    @Bean
    public OutboxPublishingService outboxPublishingService(
            List<OutboxPublisher> publishers,
            ClockProvider clock,
            GlobalStatisticsPublisher globalStatistics,
            ApplicationEventPublisher eventPublisher) {
        
        // Build publisher lookup map
        Map<String, OutboxPublisher> publisherByName = new ConcurrentHashMap<>();
        for (OutboxPublisher publisher : publishers) {
            publisherByName.put(publisher.getName(), publisher);
        }

        return new OutboxPublishingServiceImpl(
            publisherByName,
            clock,
            globalStatistics,
            eventPublisher
        );
    }
    
    /**
     * Create OutboxEventHandler bean.
     */
    @Bean
    public EventHandler<TopicPublisherPair> outboxEventHandler(OutboxPublishingService publishingService) {
        return new OutboxEventHandler(publishingService);
    }
    
    /**
     * Create map of processor configs from OutboxConfig.
     */
    @Bean
    public Map<TopicPublisherPair, OutboxProcessorConfig> outboxProcessorConfigs(
            OutboxConfig outboxConfig,
            TopicConfigurationProperties topicConfigProperties) {
        return OutboxProcessorConfig.createConfigMap(outboxConfig, topicConfigProperties);
    }
    
    /**
     * Create EventProcessor bean using EventProcessorFactory (legacy per-processor path).
     */
    @Bean("outboxEventProcessor")
    @ConditionalOnProperty(name = "crablet.outbox.shared-fetch.enabled", havingValue = "false", matchIfMissing = true)
    public EventProcessor<OutboxProcessorConfig, TopicPublisherPair> outboxEventProcessor(
            Map<TopicPublisherPair, OutboxProcessorConfig> configs,
            Map<String, TopicConfig> topicConfigs,
            LeaderElector outboxLeaderElector,
            ProgressTracker<TopicPublisherPair> progressTracker,
            EventFetcher<TopicPublisherPair> eventFetcher,
            EventHandler<TopicPublisherPair> eventHandler,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            Optional<ProcessorWakeupSourceFactory> wakeupSourceFactory,
            Optional<EventPollerConfig> eventPollerConfig) {
        return EventProcessorFactory.createProcessor(
                ProcessorSpec.<OutboxProcessorConfig, TopicPublisherPair>builder()
                        .configs(configs)
                        .leaderElector(outboxLeaderElector)
                        .progressTracker(progressTracker)
                        .eventFetcher(eventFetcher)
                        .eventHandler(eventHandler)
                        .taskScheduler(taskScheduler)
                        .eventPublisher(eventPublisher)
                        .wakeupSourceFactory(wakeupSourceFactory.orElseGet(NoopProcessorWakeupSourceFactory::new))
                        .eventPollerConfig(eventPollerConfig.orElseGet(EventPollerConfig::new))
                        .selections(topicConfigs.values())
                        .build());
    }

    /**
     * Shared-fetch variant: one position-ordered, transaction-safe DB fetch per cycle fans out to
     * all (topic, publisher) processors.
     */
    @Bean("outboxEventProcessor")
    @ConditionalOnProperty(name = "crablet.outbox.shared-fetch.enabled", havingValue = "true")
    public EventProcessor<OutboxProcessorConfig, TopicPublisherPair> outboxEventProcessorSharedFetch(
            Map<TopicPublisherPair, OutboxProcessorConfig> configs,
            Map<String, TopicConfig> topicConfigs,
            LeaderElector outboxLeaderElector,
            ProgressTracker<TopicPublisherPair> progressTracker,
            EventHandler<TopicPublisherPair> eventHandler,
            OutboxConfig outboxConfig,
            WriteDataSource writeDataSource,
            ReadDataSource readDataSource,
            ClockProvider clockProvider,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {

        Map<TopicPublisherPair, EventSelection> selections = new HashMap<>();
        for (TopicPublisherPair pair : configs.keySet()) {
            TopicConfig tc = topicConfigs.get(pair.topic());
            if (tc != null) {
                selections.put(pair, tc);
            }
        }

        return new SharedFetchModuleProcessor<>(
                configs,
                selections,
                "outbox",
                outboxLeaderElector.getInstanceId(),
                outboxLeaderElector,
                progressTracker,
                new ModuleScanProgressRepository(writeDataSource.dataSource()),
                new ProcessorScanProgressRepository(writeDataSource.dataSource()),
                eventHandler,
                readDataSource.dataSource(),
                outboxConfig.getFetchBatchSize(),
                taskScheduler,
                eventPublisher,
                TopicPublisherPair::toKey,
                new NoopProcessorWakeupSource(),
                clockProvider);
    }
    
    /**
     * Create map of topic configs from OutboxConfig.
     * This is needed by OutboxEventFetcher.
     */
    @Bean
    public Map<String, TopicConfig> topicConfigs(OutboxConfig outboxConfig) {
        return outboxConfig.getTopics();
    }
    
    /**
     * Create OutboxManagementService bean for outbox management.
     *
     * <p>Can be injected as either {@code OutboxManagementService} for outbox-specific
     * progress details, or as {@code ProcessorManagementService<TopicPublisherPair>}
     * for generic operations.
     */
    @Bean
    public OutboxManagementService outboxManagementService(
            EventProcessor<OutboxProcessorConfig, TopicPublisherPair> eventProcessor,
            ProgressTracker<TopicPublisherPair> progressTracker,
            ReadDataSource readDataSource,
            WriteDataSource writeDataSource,
            ClockProvider clockProvider) {
        ProcessorManagementService<TopicPublisherPair> delegate =
                EventProcessorFactory.createManagementService(eventProcessor, progressTracker, readDataSource);
        return new OutboxManagementService(delegate, writeDataSource.dataSource(), clockProvider);
    }
}
