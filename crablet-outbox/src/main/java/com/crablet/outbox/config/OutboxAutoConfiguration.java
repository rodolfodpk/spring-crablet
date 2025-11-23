package com.crablet.outbox.config;

import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventprocessor.leader.LeaderElector;
import com.crablet.eventprocessor.leader.LeaderElectorImpl;
import com.crablet.eventprocessor.management.ProcessorManagementService;
import com.crablet.eventprocessor.management.ProcessorManagementServiceImpl;
import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventprocessor.processor.EventProcessorImpl;
import com.crablet.eventprocessor.progress.ProgressTracker;
import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.outbox.InstanceIdProvider;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.adapter.*;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.publishing.OutboxPublishingServiceImpl;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
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
    
    // Advisory lock key for outbox (same as OutboxLeaderElector)
    private static final long OUTBOX_LOCK_KEY = 4856221667890123456L;
    
    /**
     * Create LeaderElector bean using generic LeaderElectorImpl.
     */
    @Bean
    public LeaderElector outboxLeaderElector(
            JdbcTemplate jdbcTemplate,
            InstanceIdProvider instanceIdProvider,
            ApplicationEventPublisher eventPublisher) {
        return new LeaderElectorImpl(
            jdbcTemplate,
            instanceIdProvider.getInstanceId(),
            OUTBOX_LOCK_KEY,
            eventPublisher
        );
    }
    
    /**
     * Create OutboxProgressTracker bean.
     */
    @Bean
    public ProgressTracker<TopicPublisherPair> outboxProgressTracker(JdbcTemplate jdbcTemplate) {
        return new OutboxProgressTracker(jdbcTemplate);
    }
    
    /**
     * Create OutboxEventFetcher bean.
     */
    @Bean
    public EventFetcher<TopicPublisherPair> outboxEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            OutboxConfig outboxConfig,
            Map<String, TopicConfig> topicConfigs) {
        return new OutboxEventFetcher(readDataSource, outboxConfig, topicConfigs);
    }
    
    /**
     * Create OutboxPublishingService bean.
     * This is still needed for the publishing logic.
     */
    @Bean
    public OutboxPublishingService outboxPublishingService(
            OutboxConfig config,
            JdbcTemplate jdbcTemplate,
            @Qualifier("readDataSource") DataSource readDataSource,
            List<OutboxPublisher> publishers,
            InstanceIdProvider instanceIdProvider,
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
            config,
            jdbcTemplate,
            readDataSource,
            publisherByName,
            instanceIdProvider,
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
     * Create EventProcessor bean using generic EventProcessorImpl.
     */
    @Bean
    public EventProcessor<OutboxProcessorConfig, TopicPublisherPair> outboxEventProcessor(
            Map<TopicPublisherPair, OutboxProcessorConfig> configs,
            LeaderElector leaderElector,
            ProgressTracker<TopicPublisherPair> progressTracker,
            EventFetcher<TopicPublisherPair> eventFetcher,
            EventHandler<TopicPublisherPair> eventHandler,
            @Qualifier("primaryDataSource") DataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {
        return new EventProcessorImpl<>(
            configs,
            leaderElector,
            progressTracker,
            eventFetcher,
            eventHandler,
            writeDataSource,
            taskScheduler,
            eventPublisher
        );
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
     * Create ProcessorManagementService bean for outbox management.
     */
    @Bean
    public ProcessorManagementService<TopicPublisherPair> outboxManagementService(
            EventProcessor<OutboxProcessorConfig, TopicPublisherPair> eventProcessor,
            ProgressTracker<TopicPublisherPair> progressTracker,
            JdbcTemplate jdbcTemplate) {
        return new ProcessorManagementServiceImpl<>(eventProcessor, progressTracker, jdbcTemplate);
    }
}

