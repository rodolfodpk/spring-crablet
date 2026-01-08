package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutorImpl;
import com.crablet.command.handlers.wallet.DepositCommandHandler;
import com.crablet.command.handlers.wallet.OpenWalletCommandHandler;
import com.crablet.command.handlers.wallet.TransferMoneyCommandHandler;
import com.crablet.command.handlers.wallet.WithdrawCommandHandler;
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

import javax.sql.DataSource;

@SpringBootApplication
@EnableConfigurationProperties(DataSourceProperties.class)
@ComponentScan(
    basePackages = {"com.crablet.command", "com.crablet.eventstore", "com.crablet.examples"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.crablet.eventstore.integration.TestApplication.class),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.crablet\\.eventstore\\.config\\.DataSourceConfig")
    }
)
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
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
    
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
    public EventRepository eventRepository(DataSource dataSource, EventStoreConfig config) {
        return new EventRepositoryImpl(dataSource, config);
    }
    
    @Bean
    public TestCommandHandler testCommandHandler() {
        return new TestCommandHandler();
    }
    
    @Bean
    public CommandExecutor commandExecutor(EventStore eventStore, 
                                           java.util.List<com.crablet.command.CommandHandler<?>> commandHandlers,
                                           EventStoreConfig config,
                                           ClockProvider clock,
                                           com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                           org.springframework.context.ApplicationEventPublisher eventPublisher) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }
    
    /**
     * Flyway bean to ensure migrations run before tests.
     * Migrations run immediately when bean is created.
     * Uses migrations from src/main/resources/db/migration.
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

