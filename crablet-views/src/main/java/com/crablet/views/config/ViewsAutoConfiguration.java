package com.crablet.views.config;

import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventProcessorFactory;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.views.ViewProjector;
import com.crablet.views.ViewSubscription;
import com.crablet.views.internal.ViewEventFetcher;
import com.crablet.views.internal.ViewEventHandler;
import com.crablet.views.internal.ViewProcessorConfig;
import com.crablet.views.internal.ViewProgressTracker;
import com.crablet.views.service.ViewManagementService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for views using the generic event processor.
 *
 * <p>This configuration creates all necessary beans to use the generic
 * EventProcessor with view-specific adapters.
 *
 * <p>Enabled when {@code crablet.views.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "crablet.views.enabled", havingValue = "true", matchIfMissing = false)
public class ViewsAutoConfiguration {

    // Advisory lock key for views (different from outbox)
    private static final long VIEWS_LOCK_KEY = 4856221667890123457L;

    @Bean
    public ProgressTracker<String> viewProgressTracker(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return new ViewProgressTracker(dataSource);
    }

    @Bean
    public EventFetcher<String> viewEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            @Qualifier("viewSubscriptions") Map<String, ViewSubscription> subscriptions) {
        return new ViewEventFetcher(readDataSource, subscriptions);
    }

    @Bean
    public EventHandler<String> viewEventHandler(List<ViewProjector> projectors, ApplicationEventPublisher eventPublisher) {
        return new ViewEventHandler(projectors, eventPublisher);
    }

    @Bean
    public Map<String, ViewSubscription> viewSubscriptions(List<ViewSubscription> subscriptionBeans) {
        Map<String, ViewSubscription> subscriptions = new HashMap<>();
        for (ViewSubscription subscription : subscriptionBeans) {
            subscriptions.put(subscription.getViewName(), subscription);
        }
        return subscriptions;
    }

    @Bean
    public Map<String, ViewProcessorConfig> viewProcessorConfigs(
            ViewsConfig viewsConfig,
            @Qualifier("viewSubscriptions") Map<String, ViewSubscription> subscriptions) {
        return ViewProcessorConfig.createConfigMap(viewsConfig, subscriptions);
    }

    @Bean
    public ViewManagementService viewManagementService(
            @Qualifier("viewsEventProcessor") EventProcessor<ViewProcessorConfig, String> eventProcessor,
            @Qualifier("viewProgressTracker") ProgressTracker<String> progressTracker,
            @Qualifier("readDataSource") DataSource readDataSource,
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        ProcessorManagementService<String> delegate = EventProcessorFactory.createManagementService(
            eventProcessor, progressTracker, readDataSource);
        return new ViewManagementService(delegate, primaryDataSource);
    }

    @Bean
    @org.springframework.context.annotation.DependsOn("flyway")
    public EventProcessor<ViewProcessorConfig, String> viewsEventProcessor(
            Map<String, ViewProcessorConfig> processorConfigs,
            @Qualifier("viewProgressTracker") ProgressTracker<String> progressTracker,
            @Qualifier("viewEventFetcher") EventFetcher<String> eventFetcher,
            @Qualifier("viewEventHandler") EventHandler<String> eventHandler,
            InstanceIdProvider instanceIdProvider,
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {

        return EventProcessorFactory.createProcessor(
            processorConfigs,
            "views",
            VIEWS_LOCK_KEY,
            instanceIdProvider.getInstanceId(),
            progressTracker,
            eventFetcher,
            eventHandler,
            primaryDataSource,
            taskScheduler,
            eventPublisher);
    }
}
