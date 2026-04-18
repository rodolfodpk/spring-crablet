package com.crablet.views.config;

import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventProcessorFactory;
import com.crablet.eventpoller.EventSelection;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.config.EventPollerAutoConfiguration;
import com.crablet.eventpoller.internal.sharedfetch.ModuleScanProgressRepository;
import com.crablet.eventpoller.internal.sharedfetch.ProcessorScanProgressRepository;
import com.crablet.eventpoller.internal.sharedfetch.SharedFetchModuleProcessor;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSourceFactory;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.views.ViewProjector;
import com.crablet.views.ViewSubscription;
import com.crablet.views.internal.ViewEventFetcher;
import com.crablet.views.internal.ViewEventHandler;
import com.crablet.views.internal.ViewProcessorConfig;
import com.crablet.views.internal.ViewProgressTracker;
import com.crablet.views.service.ViewManagementService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Auto-configuration for views using the generic event processor.
 *
 * <p>This configuration creates all necessary beans to use the generic
 * EventProcessor with view-specific adapters.
 *
 * <p>Enabled when {@code crablet.views.enabled=true}.
 */
@AutoConfiguration(after = EventPollerAutoConfiguration.class)
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
    @ConditionalOnProperty(name = "crablet.views.shared-fetch.enabled", havingValue = "false", matchIfMissing = true)
    public EventProcessor<ViewProcessorConfig, String> viewsEventProcessor(
            Map<String, ViewProcessorConfig> processorConfigs,
            @org.springframework.beans.factory.annotation.Qualifier("viewProgressTracker") ProgressTracker<String> progressTracker,
            @org.springframework.beans.factory.annotation.Qualifier("viewEventFetcher") EventFetcher<String> eventFetcher,
            @org.springframework.beans.factory.annotation.Qualifier("viewEventHandler") EventHandler<String> eventHandler,
            InstanceIdProvider instanceIdProvider,
            WriteDataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher,
            Optional<ProcessorWakeupSourceFactory> wakeupSourceFactory) {

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
            eventPublisher,
            wakeupSourceFactory.orElseGet(NoopProcessorWakeupSourceFactory::new));
    }

    @Bean("viewsEventProcessor")
    @ConditionalOnProperty(name = "crablet.views.shared-fetch.enabled", havingValue = "true")
    public EventProcessor<ViewProcessorConfig, String> viewsEventProcessorSharedFetch(
            Map<String, ViewProcessorConfig> processorConfigs,
            @org.springframework.beans.factory.annotation.Qualifier("viewSubscriptions") Map<String, ViewSubscription> viewSubscriptions,
            @org.springframework.beans.factory.annotation.Qualifier("viewProgressTracker") ProgressTracker<String> progressTracker,
            @org.springframework.beans.factory.annotation.Qualifier("viewEventHandler") EventHandler<String> eventHandler,
            InstanceIdProvider instanceIdProvider,
            ViewsConfig viewsConfig,
            WriteDataSource writeDataSource,
            ReadDataSource readDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {

        LeaderElector leaderElector = EventProcessorFactory.createLeaderElector(
                writeDataSource, "views", instanceIdProvider.getInstanceId(), VIEWS_LOCK_KEY, eventPublisher);

        Map<String, EventSelection> selections = new HashMap<>(viewSubscriptions);

        return new SharedFetchModuleProcessor<>(
                processorConfigs,
                selections,
                "views",
                instanceIdProvider.getInstanceId(),
                leaderElector,
                progressTracker,
                new ModuleScanProgressRepository(writeDataSource.dataSource()),
                new ProcessorScanProgressRepository(writeDataSource.dataSource()),
                eventHandler,
                readDataSource.dataSource(),
                viewsConfig.getFetchBatchSize(),
                taskScheduler,
                eventPublisher,
                Function.identity());
    }
}
