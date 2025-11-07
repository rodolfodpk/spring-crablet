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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@SpringBootApplication
@ComponentScan(
    basePackages = {"com.crablet.command", "com.crablet.eventstore", "com.crablet.examples"},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.crablet.eventstore.integration.TestApplication.class)
)
public class TestApplication {
    
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
    public CommandExecutor commandExecutor(EventStore eventStore, 
                                           java.util.List<com.crablet.command.CommandHandler<?>> commandHandlers,
                                           EventStoreConfig config,
                                           ClockProvider clock,
                                           com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                           org.springframework.context.ApplicationEventPublisher eventPublisher) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }
}

