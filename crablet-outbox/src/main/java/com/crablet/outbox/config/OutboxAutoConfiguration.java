package com.crablet.outbox.config;

import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventProcessorFactory;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSourceFactory;
import com.crablet.outbox.management.OutboxManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.internal.OutboxEventFetcher;
import com.crablet.outbox.internal.OutboxEventHandler;
import com.crablet.outbox.internal.OutboxProcessorConfig;
import com.crablet.outbox.internal.OutboxProgressTracker;
import com.crablet.outbox.internal.TopicPublisherPair;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.publishing.OutboxPublishingServiceImpl;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

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
@Configuration
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
            OutboxConfig outboxConfig,
            Map<String, TopicConfig> topicConfigs) {
        return new OutboxEventFetcher(readDataSource.dataSource(), outboxConfig, topicConfigs);
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
            CircuitBreakerRegistry circuitBreakerRegistry,
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
            circuitBreakerRegistry,
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
     * Create EventProcessor bean using EventProcessorFactory.
     */
    @Bean
    public EventProcessor<OutboxProcessorConfig, TopicPublisherPair> outboxEventProcessor(
            Map<TopicPublisherPair, OutboxProcessorConfig> configs,
            LeaderElector outboxLeaderElector,
            ProgressTracker<TopicPublisherPair> progressTracker,
            EventFetcher<TopicPublisherPair> eventFetcher,
            EventHandler<TopicPublisherPair> eventHandler,
            WriteDataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            Optional<ProcessorWakeupSourceFactory> wakeupSourceFactory) {
        return EventProcessorFactory.createProcessor(
            configs,
            outboxLeaderElector,
            progressTracker,
            eventFetcher,
            eventHandler,
            writeDataSource,
            taskScheduler,
            eventPublisher,
            wakeupSourceFactory.orElseGet(NoopProcessorWakeupSourceFactory::new));
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
            WriteDataSource writeDataSource) {
        ProcessorManagementService<TopicPublisherPair> delegate =
                EventProcessorFactory.createManagementService(eventProcessor, progressTracker, readDataSource);
        return new OutboxManagementService(delegate, writeDataSource.dataSource());
    }
}
