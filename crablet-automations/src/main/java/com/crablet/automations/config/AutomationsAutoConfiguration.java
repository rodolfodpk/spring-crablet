package com.crablet.automations.config;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.internal.AutomationDispatcher;
import com.crablet.automations.internal.AutomationEventFetcher;
import com.crablet.automations.internal.AutomationProcessorConfig;
import com.crablet.automations.internal.AutomationProgressTracker;
import com.crablet.automations.internal.AutomationWebhookClient;
import com.crablet.automations.management.AutomationManagementService;
import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.EventProcessorFactory;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
            WriteDataSource writeDataSource) {
        return new AutomationProgressTracker(writeDataSource.dataSource());
    }

    @Bean
    public Map<String, AutomationHandler> automationHandlers(
            ObjectProvider<List<AutomationHandler>> handlerBeansProvider) {
        List<AutomationHandler> handlers = handlerBeansProvider.getIfAvailable(List::of);
        validateUniqueHandlerNames(handlers);
        Map<String, AutomationHandler> map = new HashMap<>();
        for (AutomationHandler handler : handlers) {
            map.put(handler.getAutomationName(), handler);
        }
        return map;
    }

    @Bean
    public EventFetcher<String> automationEventFetcher(
            ReadDataSource readDataSource,
            @Qualifier("automationHandlers") Map<String, AutomationHandler> handlers) {
        return new AutomationEventFetcher(readDataSource.dataSource(), handlers);
    }

    @Bean
    public AutomationWebhookClient automationWebhookClient(
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectMapper objectMapper,
            ObjectProvider<List<Consumer<RestClient.Builder>>> restClientBuilderCustomizersProvider) {
        List<Consumer<RestClient.Builder>> customizers = restClientBuilderCustomizersProvider.getIfAvailable(List::of);
        return new AutomationWebhookClient(restClientBuilderProvider, objectMapper, customizers);
    }

    @Bean
    public EventHandler<String> automationEventHandler(
            @Qualifier("automationHandlers") Map<String, AutomationHandler> handlers,
            AutomationWebhookClient automationWebhookClient,
            ObjectProvider<CommandExecutor> commandExecutorProvider,
            ApplicationEventPublisher eventPublisher,
            Environment environment) {

        CommandExecutor commandExecutor = commandExecutorProvider.getIfAvailable();
        boolean hasInProcessHandler = handlers.values().stream()
                .anyMatch(handler -> handler.getWebhookUrl() == null || handler.getWebhookUrl().isBlank());
        if (hasInProcessHandler && commandExecutor == null) {
            throw new IllegalStateException(
                    "In-process AutomationHandlers require a CommandExecutor bean. " +
                    "Found handlers: " + handlers.keySet() + ". " +
                    "Ensure crablet-commands is on the classpath and a CommandExecutor bean is defined.");
        }

        return new AutomationDispatcher(handlers, automationWebhookClient, commandExecutor, eventPublisher, environment);
    }

    @Bean
    public Map<String, AutomationProcessorConfig> automationProcessorConfigs(
            AutomationsConfig automationsConfig,
            @Qualifier("automationHandlers") Map<String, AutomationHandler> handlers) {
        return AutomationProcessorConfig.createConfigMap(automationsConfig, handlers);
    }

    @Bean
    @org.springframework.context.annotation.DependsOn("flyway")
    public EventProcessor<AutomationProcessorConfig, String> automationsEventProcessor(
            @Qualifier("automationProcessorConfigs") Map<String, AutomationProcessorConfig> automationProcessorConfigs,
            @Qualifier("automationProgressTracker") ProgressTracker<String> automationProgressTracker,
            @Qualifier("automationEventFetcher") EventFetcher<String> automationEventFetcher,
            @Qualifier("automationEventHandler") EventHandler<String> automationEventHandler,
            InstanceIdProvider instanceIdProvider,
            WriteDataSource writeDataSource,
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
            ReadDataSource readDataSource) {
        return EventProcessorFactory.createManagementService(automationsEventProcessor, automationProgressTracker, readDataSource);
    }

    @Bean
    public AutomationManagementService automationManagementService(
            @Qualifier("automationProcessorManagementService") ProcessorManagementService<String> delegate,
            WriteDataSource writeDataSource) {
        return new AutomationManagementService(delegate, writeDataSource.dataSource());
    }

    private static void validateUniqueHandlerNames(List<AutomationHandler> handlers) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (AutomationHandler handler : handlers) {
            if (!seen.add(handler.getAutomationName())) {
                duplicates.add(handler.getAutomationName());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate AutomationHandler names found: " + duplicates +
                    ". Each automation name must be unique.");
        }
    }
}
