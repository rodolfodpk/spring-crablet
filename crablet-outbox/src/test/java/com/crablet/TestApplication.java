package com.crablet;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.outbox.config.GlobalStatisticsConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.leader.OutboxLeaderElector;
import com.crablet.outbox.management.OutboxManagementService;
import com.crablet.outbox.metrics.OutboxMetrics;
import com.crablet.outbox.metrics.OutboxPublisherMetrics;
import com.crablet.outbox.processor.OutboxProcessorImpl;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.publishing.OutboxPublishingServiceImpl;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@SpringBootApplication
@EnableScheduling
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
    
    @Bean
    @ConfigurationProperties(prefix = "crablet.eventstore")
    public EventStoreConfig eventStoreConfig() {
        return new EventStoreConfig();
    }
    
    @Bean
    @Primary
    public ClockProvider clockProvider() {
        return new ClockProviderImpl();
    }
    
    
    @Bean
    @Primary
    public EventStore eventStore(
            DataSource dataSource,
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock) {
        // Use same datasource for both read and write in tests
        return new EventStoreImpl(dataSource, dataSource, objectMapper, config, clock);
    }
    
    @Bean
    @ConfigurationProperties(prefix = "crablet.outbox")
    public OutboxConfig outboxConfig() {
        return new OutboxConfig();
    }
    
    @Bean
    @ConfigurationProperties(prefix = "crablet.outbox.global-statistics")
    public GlobalStatisticsConfig globalStatisticsConfig() {
        return new GlobalStatisticsConfig();
    }
    
    @Bean
    @ConfigurationProperties(prefix = "crablet.outbox.topics")
    public TopicConfigurationProperties topicConfigurationProperties() {
        return new TopicConfigurationProperties();
    }
    
    @Bean
    public OutboxManagementService outboxManagementService(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            com.crablet.outbox.processor.OutboxProcessorImpl outboxProcessor) {
        return new OutboxManagementService(jdbcTemplate, outboxProcessor);
    }
    
    @Bean
    public OutboxMetrics outboxMetrics(org.springframework.core.env.Environment environment) {
        return new OutboxMetrics(environment);
    }
    
    @Bean
    public OutboxPublisherMetrics outboxPublisherMetrics(MeterRegistry registry) {
        return new OutboxPublisherMetrics(registry);
    }
    
    @Bean
    public OutboxLeaderElector outboxLeaderElector(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, OutboxConfig config, OutboxMetrics metrics) {
        return new OutboxLeaderElector(jdbcTemplate, config, metrics);
    }
    
    @Bean
    public OutboxPublishingService outboxPublishingService(
            OutboxConfig config,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            DataSource readDataSource,
            java.util.List<com.crablet.outbox.OutboxPublisher> publishers,
            OutboxMetrics outboxMetrics,
            OutboxPublisherMetrics publisherMetrics,
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics) {
        
        // Build publisher lookup map
        java.util.Map<String, com.crablet.outbox.OutboxPublisher> publisherByName = new java.util.concurrent.ConcurrentHashMap<>();
        for (com.crablet.outbox.OutboxPublisher publisher : publishers) {
            publisherByName.put(publisher.getName(), publisher);
        }
        
        return new OutboxPublishingServiceImpl(
            config, jdbcTemplate, readDataSource, publisherByName,
            outboxMetrics, publisherMetrics, circuitBreakerRegistry, globalStatistics
        );
    }
    
    @Bean
    public OutboxProcessorImpl outboxProcessorImpl(
            OutboxConfig config,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            java.util.List<com.crablet.outbox.OutboxPublisher> publishers,
            OutboxLeaderElector leaderElector,
            OutboxPublishingService publishingService,
            OutboxMetrics metrics,
            OutboxPublisherMetrics publisherMetrics,
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics,
            TopicConfigurationProperties topicConfigProperties,
            org.springframework.scheduling.TaskScheduler taskScheduler) {
        return new OutboxProcessorImpl(config, jdbcTemplate, dataSource, publishers, 
                                       leaderElector, publishingService, metrics, 
                                       publisherMetrics, circuitBreakerRegistry, 
                                       globalStatistics, topicConfigProperties, taskScheduler);
    }
    
    @Bean
    public GlobalStatisticsPublisher globalStatisticsPublisher(GlobalStatisticsConfig config) {
        return new GlobalStatisticsPublisher(config);
    }
}
