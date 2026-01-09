package com.crablet.metrics.micrometer.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutorImpl;
import com.crablet.command.handlers.wallet.DepositCommandHandler;
import com.crablet.command.handlers.wallet.OpenWalletCommandHandler;
import com.crablet.command.handlers.wallet.TransferMoneyCommandHandler;
import com.crablet.command.handlers.wallet.WithdrawCommandHandler;
import com.crablet.eventprocessor.InstanceIdProvider;
import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.EventRepositoryImpl;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreImpl;
import com.crablet.examples.wallet.period.PeriodConfigurationProvider;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import com.crablet.examples.wallet.period.WalletStatementPeriodResolver;
import com.crablet.examples.wallet.projections.WalletBalanceProjector;
import com.crablet.metrics.micrometer.MicrometerMetricsCollector;
import com.crablet.outbox.config.GlobalStatisticsConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import com.crablet.outbox.publishing.OutboxPublishingServiceImpl;
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
    
    /**
     * ObjectMapper bean for JSON serialization.
     * Registers Java 8 time module for Instant, LocalDateTime, etc.
     */
    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
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
    @Primary
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
    
    @Bean
    public MicrometerMetricsCollector micrometerMetricsCollector(MeterRegistry registry) {
        return new MicrometerMetricsCollector(registry);
    }
    
    @Bean
    public EventRepository eventRepository(DataSource dataSource, EventStoreConfig config) {
        return new EventRepositoryImpl(dataSource, config);
    }
    
    @Bean
    public WalletBalanceProjector walletBalanceProjector() {
        return new WalletBalanceProjector();
    }
    
    @Bean
    public PeriodConfigurationProvider periodConfigurationProvider() {
        return new PeriodConfigurationProvider();
    }
    
    @Bean
    public WalletStatementPeriodResolver walletStatementPeriodResolver(
            EventRepository eventRepository,
            ClockProvider clock,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            WalletBalanceProjector balanceProjector) {
        return new WalletStatementPeriodResolver(eventRepository, clock, objectMapper, balanceProjector);
    }
    
    @Bean
    public WalletPeriodHelper walletPeriodHelper(
            WalletStatementPeriodResolver periodResolver,
            PeriodConfigurationProvider configProvider,
            WalletBalanceProjector balanceProjector) {
        return new WalletPeriodHelper(periodResolver, configProvider, balanceProjector);
    }
    
    // Command handlers for Command metrics integration tests
    @Bean
    public OpenWalletCommandHandler openWalletCommandHandler() {
        return new OpenWalletCommandHandler();
    }
    
    @Bean
    public DepositCommandHandler depositCommandHandler(WalletPeriodHelper periodHelper) {
        return new DepositCommandHandler(periodHelper);
    }
    
    @Bean
    public WithdrawCommandHandler withdrawCommandHandler(WalletPeriodHelper periodHelper) {
        return new WithdrawCommandHandler(periodHelper);
    }
    
    @Bean
    public TransferMoneyCommandHandler transferMoneyCommandHandler(WalletPeriodHelper periodHelper) {
        return new TransferMoneyCommandHandler(periodHelper);
    }
    
    @Bean
    public CommandExecutor commandExecutor(
            EventStore eventStore,
            java.util.List<com.crablet.command.CommandHandler<?>> handlers,
            ClockProvider clock,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        com.crablet.eventstore.store.EventStoreConfig config = new com.crablet.eventstore.store.EventStoreConfig();
        config.setPersistCommands(true);
        return new CommandExecutorImpl(eventStore, handlers, config, clock, objectMapper, eventPublisher);
    }
    
    @Bean
    public InstanceIdProvider instanceIdProvider(org.springframework.core.env.Environment environment) {
        return new InstanceIdProvider(environment);
    }
    
    // OutboxLeaderElector is now auto-configured
    // when crablet.outbox.enabled=true via OutboxAutoConfiguration
    
    @Bean
    public OutboxPublishingService outboxPublishingService(
            OutboxConfig config,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            DataSource readDataSource,
            java.util.List<com.crablet.outbox.OutboxPublisher> publishers,
            InstanceIdProvider instanceIdProvider,
            ClockProvider clock,
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        
        // Build publisher lookup map
        java.util.Map<String, com.crablet.outbox.OutboxPublisher> publisherByName = new java.util.concurrent.ConcurrentHashMap<>();
        for (com.crablet.outbox.OutboxPublisher publisher : publishers) {
            publisherByName.put(publisher.getName(), publisher);
        }
        
        return new OutboxPublishingServiceImpl(
            config, jdbcTemplate, readDataSource, publisherByName,
            instanceIdProvider, clock, circuitBreakerRegistry, globalStatistics, eventPublisher
        );
    }
    
    // OutboxProcessorImpl and OutboxManagementService are now auto-configured
    // when crablet.outbox.enabled=true via OutboxAutoConfiguration
    // No manual bean configuration needed
    
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

