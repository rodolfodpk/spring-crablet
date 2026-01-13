package com.crablet.views.config;

import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventprocessor.InstanceIdProvider;
import com.crablet.eventprocessor.leader.LeaderElector;
import com.crablet.eventprocessor.leader.LeaderElectorImpl;
import com.crablet.eventprocessor.management.ProcessorManagementService;
import com.crablet.eventprocessor.management.ProcessorManagementServiceImpl;
import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventprocessor.processor.EventProcessorImpl;
import com.crablet.eventprocessor.progress.ProgressTracker;
import com.crablet.views.ViewProjector;
import com.crablet.views.adapter.ViewEventFetcher;
import com.crablet.views.adapter.ViewEventHandler;
import com.crablet.views.adapter.ViewProcessorConfig;
import com.crablet.views.adapter.ViewProgressTracker;
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
    
    /**
     * Create LeaderElector bean using generic LeaderElectorImpl.
     */
    @Bean
    public LeaderElector viewsLeaderElector(
            @Qualifier("primaryDataSource") DataSource dataSource,
            InstanceIdProvider instanceIdProvider,
            ApplicationEventPublisher eventPublisher) {
        return new LeaderElectorImpl(
            dataSource,
            instanceIdProvider.getInstanceId(),
            VIEWS_LOCK_KEY,
            eventPublisher
        );
    }
    
    /**
     * Create ViewProgressTracker bean.
     */
    @Bean
    public ProgressTracker<String> viewProgressTracker(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return new ViewProgressTracker(dataSource);
    }
    
    /**
     * Create ViewEventFetcher bean.
     */
    @Bean
    public EventFetcher<String> viewEventFetcher(
            @Qualifier("readDataSource") DataSource readDataSource,
            @Qualifier("viewSubscriptions") Map<String, ViewSubscriptionConfig> subscriptions) {
        return new ViewEventFetcher(readDataSource, subscriptions);
    }
    
    /**
     * Create ViewEventHandler bean.
     * Collects all ViewProjector beans registered by users.
     */
    @Bean
    public EventHandler<String> viewEventHandler(List<ViewProjector> projectors) {
        return new ViewEventHandler(projectors);
    }
    
    /**
     * Create map of subscriptions from ViewSubscriptionConfig beans.
     * Users provide ViewSubscriptionConfig (or ViewSubscription) beans, Spring collects them here.
     */
    @Bean
    public Map<String, ViewSubscriptionConfig> viewSubscriptions(List<ViewSubscriptionConfig> subscriptionBeans) {
        Map<String, ViewSubscriptionConfig> subscriptions = new HashMap<>();
        for (ViewSubscriptionConfig subscription : subscriptionBeans) {
            subscriptions.put(subscription.getViewName(), subscription);
        }
        return subscriptions;
    }
    
    /**
     * Create map of processor configs from ViewsConfig and subscriptions.
     */
    @Bean
    public Map<String, ViewProcessorConfig> viewProcessorConfigs(
            ViewsConfig viewsConfig,
            @Qualifier("viewSubscriptions") Map<String, ViewSubscriptionConfig> subscriptions) {
        return ViewProcessorConfig.createConfigMap(viewsConfig, subscriptions);
    }
    
    /**
     * Create ViewManagementService bean for view management.
     * 
     * <p>This service extends ProcessorManagementService with detailed progress monitoring.
     * It can be injected as either ViewManagementService or ProcessorManagementService<String>
     * for backward compatibility.
     */
    @Bean
    public ViewManagementService viewManagementService(
            EventProcessor<ViewProcessorConfig, String> eventProcessor,
            ProgressTracker<String> progressTracker,
            @Qualifier("readDataSource") DataSource readDataSource,
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        // Create delegate
        ProcessorManagementService<String> delegate = new ProcessorManagementServiceImpl<>(
            eventProcessor, progressTracker, readDataSource);
        
        // Return wrapper with enhanced functionality
        return new ViewManagementService(delegate, primaryDataSource);
    }
    
    /**
     * Create EventProcessor bean using generic EventProcessorImpl.
     * Depends on Flyway to ensure database migrations complete before schedulers start.
     */
    @Bean
    @org.springframework.context.annotation.DependsOn("flyway")
    public EventProcessor<ViewProcessorConfig, String> viewsEventProcessor(
            Map<String, ViewProcessorConfig> processorConfigs,
            LeaderElector leaderElector,
            ProgressTracker<String> progressTracker,
            EventFetcher<String> eventFetcher,
            @Qualifier("viewEventHandler") EventHandler<String> eventHandler,
            @Qualifier("primaryDataSource") DataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {
        
        return new EventProcessorImpl<>(
            processorConfigs,
            leaderElector,
            progressTracker,
            eventFetcher,
            eventHandler,
            writeDataSource,
            taskScheduler,
            eventPublisher
        );
    }
}

