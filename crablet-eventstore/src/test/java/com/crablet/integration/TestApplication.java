package com.crablet.integration;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.commands.CommandExecutor;
import com.crablet.eventstore.commands.CommandExecutorImpl;
import com.crablet.eventstore.query.EventTestHelper;
import com.crablet.eventstore.query.EventTestHelperImpl;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreImpl;
import com.crablet.eventstore.store.EventStoreMetrics;
import com.crablet.wallet.domain.projections.WalletBalanceProjector;
import com.crablet.wallet.features.deposit.DepositCommandHandler;
import com.crablet.wallet.features.openwallet.OpenWalletCommandHandler;
import com.crablet.wallet.features.transfer.TransferMoneyCommandHandler;
import com.crablet.wallet.features.withdraw.WithdrawCommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@SpringBootApplication
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
    
    @Bean
    public OpenWalletCommandHandler openWalletCommandHandler() {
        return new OpenWalletCommandHandler();
    }
    
    @Bean  
    public DepositCommandHandler depositCommandHandler(WalletBalanceProjector projector) {
        return new DepositCommandHandler(projector);
    }
    
    @Bean
    public WithdrawCommandHandler withdrawCommandHandler(WalletBalanceProjector projector) {
        return new WithdrawCommandHandler(projector);
    }
    
    @Bean
    public TransferMoneyCommandHandler transferMoneyCommandHandler() {
        return new TransferMoneyCommandHandler();
    }
    
    @Bean
    public WalletBalanceProjector walletBalanceProjector() {
        return new WalletBalanceProjector();
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
    public EventStoreMetrics eventStoreMetrics(MeterRegistry registry) {
        return new EventStoreMetrics(registry);
    }
    
    @Bean
    public EventTestHelper eventTestHelper(DataSource dataSource, EventStoreConfig config) {
        return new EventTestHelperImpl(dataSource, config);
    }
    
    @Bean
    public CommandExecutor commandExecutor(EventStore eventStore, 
                                           java.util.List<com.crablet.eventstore.commands.CommandHandler<?>> commandHandlers,
                                           EventStoreConfig config,
                                           EventStoreMetrics metrics) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, metrics);
    }
}
