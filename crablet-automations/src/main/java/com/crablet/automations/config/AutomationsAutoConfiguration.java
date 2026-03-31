package com.crablet.automations.config;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.adapter.AutomationDispatcher;
import com.crablet.automations.adapter.AutomationEventFetcher;
import com.crablet.automations.adapter.AutomationProcessorConfig;
import com.crablet.automations.adapter.AutomationProgressTracker;
import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.leader.LeaderElectorImpl;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.processor.EventProcessorImpl;
import com.crablet.eventpoller.progress.ProgressTracker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for automations using the generic event processor.
 * Enabled when {@code crablet.automations.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "crablet.automations.enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(AutomationsConfig.class)
public class AutomationsAutoConfiguration {

    // Advisory lock key for automations (distinct from views and outbox)
    private static final long AUTOMATIONS_LOCK_KEY = 4856221667890123458L;

    @Bean
    public LeaderElector automationsLeaderElector(
            @Qualifier("primaryDataSource") DataSource dataSource,
            InstanceIdProvider instanceIdProvider,
            ApplicationEventPublisher eventPublisher) {
        return new LeaderElectorImpl(
            dataSource,
            instanceIdProvider.getInstanceId(),
            AUTOMATIONS_LOCK_KEY,
            eventPublisher
        );
    }

    @Bean
    public ProgressTracker<String> automationProgressTracker(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return new AutomationProgressTracker(dataSource);
    }

    @Bean
    public Map<String, AutomationSubscription> automationSubscriptions(List<AutomationSubscription> subscriptionBeans) {
        Map<String, AutomationSubscription> subscriptions = new HashMap<>();
        for (AutomationSubscription subscription : subscriptionBeans) {
            subscriptions.put(subscription.getAutomationName(), subscription);
        }
        return subscriptions;
    }

    @Bean
    public EventFetcher<String> automationEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            @Qualifier("automationSubscriptions") Map<String, AutomationSubscription> subscriptions) {
        return new AutomationEventFetcher(readDataSource, subscriptions);
    }

    @Bean
    public EventHandler<String> automationEventHandler(
            List<AutomationHandler> automations,
            CommandExecutor commandExecutor) {
        return new AutomationDispatcher(automations, commandExecutor);
    }

    @Bean
    public Map<String, AutomationProcessorConfig> automationProcessorConfigs(
            AutomationsConfig automationsConfig,
            @Qualifier("automationSubscriptions") Map<String, AutomationSubscription> subscriptions) {
        return AutomationProcessorConfig.createConfigMap(automationsConfig, subscriptions);
    }

    @Bean
    @org.springframework.context.annotation.DependsOn("flyway")
    public EventProcessor<AutomationProcessorConfig, String> automationsEventProcessor(
            @Qualifier("automationProcessorConfigs") Map<String, AutomationProcessorConfig> automationProcessorConfigs,
            @Qualifier("automationsLeaderElector") LeaderElector automationsLeaderElector,
            @Qualifier("automationProgressTracker") ProgressTracker<String> automationProgressTracker,
            @Qualifier("automationEventFetcher") EventFetcher<String> automationEventFetcher,
            @Qualifier("automationEventHandler") EventHandler<String> automationEventHandler,
            @Qualifier("primaryDataSource") DataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {

        return new EventProcessorImpl<>(
            automationProcessorConfigs,
            automationsLeaderElector,
            automationProgressTracker,
            automationEventFetcher,
            automationEventHandler,
            writeDataSource,
            taskScheduler,
            eventPublisher
        );
    }
}
