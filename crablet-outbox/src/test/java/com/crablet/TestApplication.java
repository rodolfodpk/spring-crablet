package com.crablet;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.outbox.config.GlobalStatisticsConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.eventprocessor.InstanceIdProvider;
// Old classes removed - using auto-configuration with generic processor
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@SpringBootApplication
@EnableConfigurationProperties(DataSourceProperties.class)
@ComponentScan(basePackages = {"com.crablet", "com.crablet.outbox", "com.crablet.eventstore", "com.crablet.eventprocessor"},
               excludeFilters = {
                   @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, 
                                        classes = {com.crablet.eventstore.integration.TestApplication.class}),
                   @ComponentScan.Filter(type = FilterType.REGEX, 
                                        pattern = "com\\.crablet\\.eventstore\\.config\\.DataSourceConfig")
               })
@EnableScheduling
public class TestApplication {
    
    /**
     * Primary DataSource bean (required by crablet-views if enabled).
     * DataSourceProperties is auto-configured by Spring Boot via @EnableConfigurationProperties.
     */
    @Bean(name = "primaryDataSource")
    @Primary
    public DataSource primaryDataSource(DataSourceProperties properties) {
        return DataSourceBuilder.create()
            .type(com.zaxxer.hikari.HikariDataSource.class)
            .url(properties.getUrl())
            .username(properties.getUsername())
            .password(properties.getPassword())
            .driverClassName(properties.getDriverClassName())
            .build();
    }
    
    /**
     * Read DataSource bean (required by crablet-views if enabled).
     * For this test app, we use the same DataSource for reads and writes.
     */
    @Bean(name = "readDataSource")
    public DataSource readDataSource(@org.springframework.beans.factory.annotation.Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return primaryDataSource;
    }
    
    /**
     * ObjectMapper bean for JSON serialization.
     * Registers Java 8 time module for Instant, LocalDateTime, etc.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
    
    // DataSource beans (readDataSource and primaryDataSource) are provided by
    // com.crablet.eventstore.config.DataSourceConfig from crablet-eventstore module
    
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
    @org.springframework.context.annotation.DependsOn("primaryDataSource")
    public org.flywaydb.core.Flyway flyway(@org.springframework.beans.factory.annotation.Qualifier("primaryDataSource") DataSource dataSource) {
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
