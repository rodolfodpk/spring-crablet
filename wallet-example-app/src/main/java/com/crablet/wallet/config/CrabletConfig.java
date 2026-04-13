package com.crablet.wallet.config;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.examples.wallet.period.PeriodConfigurationProvider;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import com.crablet.examples.wallet.period.WalletStatementPeriodResolver;
import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-specific bean configuration.
 * <p>
 * Framework infrastructure (EventStore, CommandExecutor, DataSources, TaskScheduler,
 * InstanceIdProvider, ClockProvider, ObjectMapper) is auto-configured by Spring Boot
 * and the crablet modules. Only beans that are wallet-domain-specific or
 * that override framework defaults belong here.
 */
@Configuration
public class CrabletConfig {

    /**
     * SimpleMeterRegistry for local development.
     * In production, Spring Boot Actuator provides a real registry automatically.
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    // --- Wallet domain beans ---

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
            ObjectMapper objectMapper,
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
}
