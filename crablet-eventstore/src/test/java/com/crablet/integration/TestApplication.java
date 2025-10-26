package com.crablet.integration;

import com.crablet.clock.ClockProvider;
import com.crablet.clock.ClockProviderImpl;
import com.crablet.store.EventStore;
import com.crablet.store.EventStoreConfig;
import com.crablet.store.EventStoreImpl;
import com.crablet.wallet.domain.projections.WalletBalanceProjector;
import com.crablet.wallet.features.deposit.DepositCommandHandler;
import com.crablet.wallet.features.openwallet.OpenWalletCommandHandler;
import com.crablet.wallet.features.transfer.TransferMoneyCommandHandler;
import com.crablet.wallet.features.transfer.TransferStateProjector;
import com.crablet.wallet.features.withdraw.WithdrawCommandHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@SpringBootApplication
@ComponentScan(basePackages = {"com.crablet", "com.wallets"})
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
    public TransferMoneyCommandHandler transferMoneyCommandHandler(
            WalletBalanceProjector balanceProjector,
            TransferStateProjector transferProjector) {
        return new TransferMoneyCommandHandler(balanceProjector, transferProjector);
    }
    
    @Bean
    public WalletBalanceProjector walletBalanceProjector() {
        return new WalletBalanceProjector();
    }
    
    @Bean
    public TransferStateProjector transferStateProjector() {
        return new TransferStateProjector();
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
            @Qualifier("primaryDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock) {
        return new EventStoreImpl(writeDataSource, readDataSource, objectMapper, config, clock);
    }
}
