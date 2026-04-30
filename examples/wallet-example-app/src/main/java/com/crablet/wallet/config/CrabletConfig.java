package com.crablet.wallet.config;

import com.crablet.command.web.CommandApiExposedCommands;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.examples.wallet.period.PeriodConfigurationProvider;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import com.crablet.examples.wallet.period.WalletStatementPeriodResolver;
import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;
import com.crablet.outbox.config.GlobalStatisticsConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishers.LogPublisher;
import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties({OutboxConfig.class, GlobalStatisticsConfig.class, TopicConfigurationProperties.class})
public class CrabletConfig {

    // --- Outbox ---

    /**
     * Log-based outbox publisher for development and example purposes.
     * Forwards every event to SLF4J.  Replace with a real publisher (Kafka, HTTP, etc.)
     * in production.
     */
    @Bean
    public GlobalStatisticsPublisher globalStatisticsPublisher(GlobalStatisticsConfig config) {
        return new GlobalStatisticsPublisher(config);
    }

    @Bean
    @ConditionalOnMissingBean(name = "logPublisher")
    public LogPublisher logPublisher() {
        return new LogPublisher();
    }

    // --- Command API exposure ---

    /**
     * Expose all wallet commands via the generic HTTP command API (POST /api/commands).
     * Any command class under com.crablet.examples.wallet is reachable; all others return 404.
     */
    @Bean
    public CommandApiExposedCommands commandApiExposedCommands() {
        return CommandApiExposedCommands.fromPackages("com.crablet.examples.wallet");
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
