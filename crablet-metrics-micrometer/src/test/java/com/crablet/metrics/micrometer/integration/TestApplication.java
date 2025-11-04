package com.crablet.metrics.micrometer.integration;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreImpl;
import com.crablet.metrics.micrometer.MicrometerMetricsCollector;
import com.crablet.outbox.InstanceIdProvider;
import com.crablet.outbox.config.GlobalStatisticsConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.leader.OutboxLeaderElector;
import com.crablet.outbox.management.OutboxManagementService;
import com.crablet.outbox.processor.OutboxProcessorImpl;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.publishing.OutboxPublishingServiceImpl;
import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutorImpl;
import com.crablet.command.handlers.DepositCommandHandler;
import com.crablet.command.handlers.OpenWalletCommandHandler;
import com.crablet.command.handlers.WithdrawCommandHandler;
import com.crablet.command.handlers.TransferMoneyCommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Test application for metrics integration tests.
 * Sets up EventStore and Outbox with Spring Events and MicrometerMetricsCollector.
 */
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
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
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
    @Primary
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
    
    @Bean
    public MicrometerMetricsCollector micrometerMetricsCollector(MeterRegistry registry) {
        return new MicrometerMetricsCollector(registry);
    }
    
    // Command handlers for Command metrics integration tests
    @Bean
    public OpenWalletCommandHandler openWalletCommandHandler() {
        return new OpenWalletCommandHandler();
    }
    
    @Bean
    public DepositCommandHandler depositCommandHandler() {
        return new DepositCommandHandler();
    }
    
    @Bean
    public WithdrawCommandHandler withdrawCommandHandler() {
        return new WithdrawCommandHandler();
    }
    
    @Bean
    public TransferMoneyCommandHandler transferMoneyCommandHandler() {
        return new TransferMoneyCommandHandler();
    }
    
    @Bean
    public CommandExecutor commandExecutor(
            EventStore eventStore,
            java.util.List<com.crablet.command.CommandHandler<?>> handlers,
            ClockProvider clock,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        com.crablet.eventstore.store.EventStoreConfig config = new com.crablet.eventstore.store.EventStoreConfig();
        config.setPersistCommands(true);
        return new CommandExecutorImpl(eventStore, handlers, config, clock, objectMapper);
    }
    
    @Bean
    public InstanceIdProvider instanceIdProvider(org.springframework.core.env.Environment environment) {
        return new InstanceIdProvider(environment);
    }
    
    @Bean
    public OutboxLeaderElector outboxLeaderElector(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, 
            OutboxConfig config,
            InstanceIdProvider instanceIdProvider) {
        return new OutboxLeaderElector(jdbcTemplate, config, instanceIdProvider);
    }
    
    @Bean
    public OutboxPublishingService outboxPublishingService(
            OutboxConfig config,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            DataSource readDataSource,
            java.util.List<com.crablet.outbox.OutboxPublisher> publishers,
            InstanceIdProvider instanceIdProvider,
            ClockProvider clock,
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics) {
        
        // Build publisher lookup map
        java.util.Map<String, com.crablet.outbox.OutboxPublisher> publisherByName = new java.util.concurrent.ConcurrentHashMap<>();
        for (com.crablet.outbox.OutboxPublisher publisher : publishers) {
            publisherByName.put(publisher.getName(), publisher);
        }
        
        return new OutboxPublishingServiceImpl(
            config, jdbcTemplate, readDataSource, publisherByName,
            instanceIdProvider, clock, circuitBreakerRegistry, globalStatistics
        );
    }
    
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(name = "outboxPublisher")
    public OutboxProcessorImpl outboxProcessorImpl(
            OutboxConfig config,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            java.util.List<com.crablet.outbox.OutboxPublisher> publishers,
            OutboxLeaderElector leaderElector,
            OutboxPublishingService publishingService,
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics,
            TopicConfigurationProperties topicConfigProperties,
            org.springframework.scheduling.TaskScheduler taskScheduler) {
        return new OutboxProcessorImpl(config, jdbcTemplate, dataSource, publishers, 
                                       leaderElector, publishingService, 
                                       circuitBreakerRegistry, 
                                       globalStatistics, topicConfigProperties, taskScheduler);
    }
    
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(name = "outboxPublisher")
    public OutboxManagementService outboxManagementService(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            OutboxProcessorImpl outboxProcessor) {
        return new OutboxManagementService(jdbcTemplate, outboxProcessor);
    }
    
    @Bean
    public GlobalStatisticsPublisher globalStatisticsPublisher(GlobalStatisticsConfig config) {
        return new GlobalStatisticsPublisher(config);
    }
}

