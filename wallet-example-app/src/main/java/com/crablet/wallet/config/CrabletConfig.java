package com.crablet.wallet.config;

import com.crablet.command.CommandExecutor;
import com.crablet.command.internal.CommandExecutorImpl;
import com.crablet.eventpoller.InstanceIdProvider;
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
import com.crablet.automations.AutomationSubscription;
import com.crablet.views.config.ViewsConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;
import java.util.List;

/**
 * Configuration for Crablet components.
 */
@Configuration
public class CrabletConfig {

    /**
     * Expose the auto-configured DataSource as "primaryDataSource" for framework modules that use @Qualifier.
     * In this example app, primary and read use the same datasource (no read replica).
     */
    @Bean("primaryDataSource")
    public DataSource primaryDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Bean("readDataSource")
    public DataSource readDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(@Qualifier("primaryDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @Bean
    @ConfigurationProperties(prefix = "crablet.eventstore")
    public EventStoreConfig eventStoreConfig() {
        return new EventStoreConfig();
    }

    @Bean
    @ConfigurationProperties(prefix = "crablet.views")
    public ViewsConfig viewsConfig() {
        return new ViewsConfig();
    }


    @Bean
    public InstanceIdProvider instanceIdProvider(Environment environment) {
        return new InstanceIdProvider(environment);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("views-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
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
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock,
            ApplicationEventPublisher eventPublisher) {
        // Use same datasource for read and write in this example
        return new EventStoreImpl(
                dataSource, dataSource, objectMapper, config, clock, eventPublisher);
    }

    @Bean
    public EventRepository eventRepository(DataSource dataSource, EventStoreConfig config) {
        return new EventRepositoryImpl(dataSource, config);
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

    @Bean
    public CommandExecutor commandExecutor(
            EventStore eventStore,
            List<com.crablet.command.CommandHandler<?>> commandHandlers,
            EventStoreConfig config,
            ClockProvider clock,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
    }

    @Bean
    public AutomationSubscription walletOpenedWelcomeNotificationSubscription() {
        return AutomationSubscription.builder("wallet-opened-welcome-notification")
                .eventTypes(com.crablet.eventstore.EventType.type(
                        com.crablet.examples.wallet.events.WalletOpened.class))
                .webhookUrl("http://localhost:8080/api/automations/wallet-opened")
                .build();
    }
}
