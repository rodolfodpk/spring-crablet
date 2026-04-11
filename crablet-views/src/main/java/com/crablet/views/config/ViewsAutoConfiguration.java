package com.crablet.views.config;

import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventProcessorFactory;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.views.ViewProjector;
import com.crablet.views.ViewSubscription;
import com.crablet.views.internal.ViewEventFetcher;
import com.crablet.views.internal.ViewEventHandler;
import com.crablet.views.internal.ViewProcessorConfig;
import com.crablet.views.internal.ViewProgressTracker;
import com.crablet.views.service.ViewManagementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

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
@EnableConfigurationProperties(ViewsConfig.class)
public class ViewsAutoConfiguration {

    // Advisory lock key for views (different from outbox)
    private static final long VIEWS_LOCK_KEY = 4856221667890123457L;

    @Bean
    public ProgressTracker<String> viewProgressTracker(
            WriteDataSource writeDataSource) {
        return new ViewProgressTracker(writeDataSource.dataSource());
    }

    @Bean
    public EventFetcher<String> viewEventFetcher(
            ReadDataSource readDataSource,
            @org.springframework.beans.factory.annotation.Qualifier("viewSubscriptions") Map<String, ViewSubscription> subscriptions) {
        return new ViewEventFetcher(readDataSource.dataSource(), subscriptions);
    }

    @Bean
    public EventHandler<String> viewEventHandler(
            List<ViewProjector> projectors,
            ApplicationEventPublisher eventPublisher) {
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
            @org.springframework.beans.factory.annotation.Qualifier("viewSubscriptions") Map<String, ViewSubscription> subscriptions) {
        return ViewProcessorConfig.createConfigMap(viewsConfig, subscriptions);
    }

    @Bean
    public ViewManagementService viewManagementService(
            @org.springframework.beans.factory.annotation.Qualifier("viewsEventProcessor") EventProcessor<ViewProcessorConfig, String> eventProcessor,
            @org.springframework.beans.factory.annotation.Qualifier("viewProgressTracker") ProgressTracker<String> progressTracker,
            ReadDataSource readDataSource,
            WriteDataSource writeDataSource) {
        ProcessorManagementService<String> delegate = EventProcessorFactory.createManagementService(
            eventProcessor, progressTracker, readDataSource);
        return new ViewManagementService(delegate, writeDataSource.dataSource());
    }

    @Bean
    @org.springframework.context.annotation.DependsOn("flyway")
    public EventProcessor<ViewProcessorConfig, String> viewsEventProcessor(
            Map<String, ViewProcessorConfig> processorConfigs,
            @org.springframework.beans.factory.annotation.Qualifier("viewProgressTracker") ProgressTracker<String> progressTracker,
            @org.springframework.beans.factory.annotation.Qualifier("viewEventFetcher") EventFetcher<String> eventFetcher,
            @org.springframework.beans.factory.annotation.Qualifier("viewEventHandler") EventHandler<String> eventHandler,
            InstanceIdProvider instanceIdProvider,
            WriteDataSource writeDataSource,
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
            writeDataSource,
            taskScheduler,
            eventPublisher);
    }
}
