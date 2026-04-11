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
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Application-specific bean configuration.
 * <p>
 * Framework infrastructure (EventStore, CommandExecutor, DataSources, TaskScheduler,
 * InstanceIdProvider, ClockProvider) is auto-configured by the crablet modules via
 * Spring Boot auto-configuration. Only beans that are wallet-domain-specific or
 * that override framework defaults belong here.
 */
@Configuration
public class CrabletConfig {

    /**
     * Override the default ObjectMapper to disable timestamp serialization for dates.
     * This ensures ISO-8601 strings in JSON responses instead of epoch millis.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

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
