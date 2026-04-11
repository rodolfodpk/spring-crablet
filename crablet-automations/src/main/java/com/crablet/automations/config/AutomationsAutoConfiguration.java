package com.crablet.automations.config;

import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.internal.AutomationDispatcher;
import com.crablet.automations.internal.AutomationEventFetcher;
import com.crablet.automations.internal.AutomationProcessorConfig;
import com.crablet.automations.internal.AutomationProgressTracker;
import com.crablet.automations.management.AutomationManagementService;
import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventProcessorFactory;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;

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
    public RestClient automationRestClient(AutomationsConfig automationsConfig) {
        return RestClient.builder()
                .build();
    }

    @Bean
    public EventHandler<String> automationEventHandler(
            @Qualifier("automationSubscriptions") Map<String, AutomationSubscription> subscriptions,
            RestClient automationRestClient,
            ApplicationEventPublisher eventPublisher,
            Environment environment) {
        return new AutomationDispatcher(
                subscriptions,
                automationRestClient,
                eventPublisher,
                environment
        );
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
            @Qualifier("automationProgressTracker") ProgressTracker<String> automationProgressTracker,
            @Qualifier("automationEventFetcher") EventFetcher<String> automationEventFetcher,
            @Qualifier("automationEventHandler") EventHandler<String> automationEventHandler,
            InstanceIdProvider instanceIdProvider,
            @Qualifier("primaryDataSource") DataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {

        return EventProcessorFactory.createProcessor(
            automationProcessorConfigs,
            "automations",
            AUTOMATIONS_LOCK_KEY,
            instanceIdProvider.getInstanceId(),
            automationProgressTracker,
            automationEventFetcher,
            automationEventHandler,
            writeDataSource,
            taskScheduler,
            eventPublisher
        );
    }

    @Bean
    public ProcessorManagementService<String> automationProcessorManagementService(
            @Qualifier("automationsEventProcessor") EventProcessor<AutomationProcessorConfig, String> automationsEventProcessor,
            @Qualifier("automationProgressTracker") ProgressTracker<String> automationProgressTracker,
            @Qualifier("readDataSource") DataSource readDataSource) {
        return EventProcessorFactory.createManagementService(automationsEventProcessor, automationProgressTracker, readDataSource);
    }

    @Bean
    public AutomationManagementService automationManagementService(
            @Qualifier("automationProcessorManagementService") ProcessorManagementService<String> delegate,
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return new AutomationManagementService(delegate, dataSource);
    }
}
