package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutors;
import com.crablet.examples.wallet.commands.DepositCommandHandler;
import com.crablet.examples.wallet.commands.OpenWalletCommandHandler;
import com.crablet.examples.wallet.commands.TransferMoneyCommandHandler;
import com.crablet.examples.wallet.commands.WithdrawCommandHandler;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.internal.EventRepositoryImpl;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.internal.EventStoreImpl;
import com.crablet.examples.wallet.period.PeriodConfigurationProvider;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import com.crablet.examples.wallet.period.WalletStatementPeriodResolver;
import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import com.crablet.test.config.CrabletFlywayConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@SpringBootApplication
@Import(CrabletFlywayConfiguration.class)
@ComponentScan(
    basePackages = {"com.crablet.command", "com.crablet.eventstore", "com.crablet.examples"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = AutoConfiguration.class)
    }
)
public class TestApplication {

    /**
     * ObjectMapper bean for JSON serialization.
     * Registers Java 8 time module for Instant, LocalDateTime, etc.
     */
    @Bean
    public tools.jackson.databind.ObjectMapper objectMapper() {
        tools.jackson.databind.ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
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
    public WalletBalanceStateProjector walletBalanceStateProjector() {
        return new WalletBalanceStateProjector();
    }
    
    @Bean
    public PeriodConfigurationProvider periodConfigurationProvider() {
        return new PeriodConfigurationProvider();
    }
    
    @Bean
    public WalletStatementPeriodResolver walletStatementPeriodResolver(
            EventRepository eventRepository,
            ClockProvider clock,
            tools.jackson.databind.ObjectMapper objectMapper,
            WalletBalanceStateProjector balanceProjector) {
        return new WalletStatementPeriodResolver(eventRepository, clock, objectMapper, balanceProjector);
    }
    
    @Bean
    public WalletPeriodHelper walletPeriodHelper(
            WalletStatementPeriodResolver periodResolver,
            PeriodConfigurationProvider configProvider,
            WalletBalanceStateProjector balanceProjector,
            ClockProvider clockProvider) {
        return new WalletPeriodHelper(periodResolver, configProvider, balanceProjector, clockProvider);
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
    @DependsOn("flyway")
    public EventStore eventStore(
            DataSource dataSource,
            tools.jackson.databind.ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock,
            ApplicationEventPublisher eventPublisher) {
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
                                           tools.jackson.databind.ObjectMapper objectMapper,
                                           ApplicationEventPublisher eventPublisher) {
        return CommandExecutors.create(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }
    
}
