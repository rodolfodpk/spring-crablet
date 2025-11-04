package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutorImpl;
import com.crablet.command.handlers.DepositCommandHandler;
import com.crablet.command.handlers.OpenWalletCommandHandler;
import com.crablet.command.handlers.TransferMoneyCommandHandler;
import com.crablet.command.handlers.WithdrawCommandHandler;
import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.EventRepositoryImpl;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreImpl;
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
    public EventRepository eventRepository(DataSource dataSource, EventStoreConfig config) {
        return new EventRepositoryImpl(dataSource, config);
    }
    
    @Bean
    public CommandExecutor commandExecutor(EventStore eventStore, 
                                           java.util.List<com.crablet.command.CommandHandler<?>> commandHandlers,
                                           EventStoreConfig config,
                                           ClockProvider clock,
                                           com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper);
    }
}

