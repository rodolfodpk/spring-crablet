package com.crablet;

import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.internal.EventStoreImpl;
import com.crablet.outbox.config.GlobalStatisticsConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@SpringBootApplication
@ComponentScan(basePackages = {"com.crablet", "com.crablet.outbox", "com.crablet.eventstore", "com.crablet.eventpoller"},
               excludeFilters = {
                   @ComponentScan.Filter(type = FilterType.ANNOTATION,
                                        classes = AutoConfiguration.class)
               })
@EnableScheduling
public class TestApplication {

    /**
     * ObjectMapper bean for JSON serialization.
     * Registers Java 8 time module for Instant, LocalDateTime, etc.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = JsonMapper.builder().build();
        return mapper;
    }
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
            ClockProvider clock,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        // Use same datasource for both read and write in tests
        return new EventStoreImpl(dataSource, dataSource, objectMapper, config, clock, eventPublisher);
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
    public InstanceIdProvider instanceIdProvider(org.springframework.core.env.Environment environment) {
        return new InstanceIdProvider(environment);
    }
    
    // Spring Boot provides the base DataSource; Crablet auto-configuration adds
    // the framework's typed read/write datasource beans and compatibility aliases.
    
    // OutboxManagementService, OutboxLeaderElector, OutboxPublishingService, and EventProcessor
    // are now created automatically by OutboxAutoConfiguration when crablet.outbox.enabled=true
    // 
    // For tests that need outbox, ensure crablet.outbox.enabled=true in test properties
    // For tests that don't need outbox, keep crablet.outbox.enabled=false (default)
    
    @Bean
    public GlobalStatisticsPublisher globalStatisticsPublisher(GlobalStatisticsConfig config) {
        return new GlobalStatisticsPublisher(config);
    }
    
    /**
     * Flyway bean to ensure migrations run before tests.
     * Migrations run immediately when bean is created.
     * Uses migrations from src/test/resources/db/migration.
     */
    @Bean
    public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TestApplication.class);
        log.info("[TestApplication] Flyway bean creation started at {}", java.time.Instant.now());
        
        org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        
        log.info("[TestApplication] Starting Flyway migration at {}", java.time.Instant.now());
        flyway.migrate();
        log.info("[TestApplication] Flyway migration completed at {}", java.time.Instant.now());
        
        return flyway;
    }
}
