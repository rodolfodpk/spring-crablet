package com.crablet.automations.config;

import com.crablet.automations.AutomationDecision;
import com.crablet.automations.AutomationHandler;
import com.crablet.automations.internal.AutomationProcessorConfig;
import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.internal.sharedfetch.SharedFetchModuleProcessor;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.eventstore.StoredEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AutomationsAutoConfiguration Unit Tests")
class AutomationsAutoConfigurationTest {

    private final AutomationsAutoConfiguration autoConfiguration = new AutomationsAutoConfiguration();

    @Test
    @DisplayName("Should reject duplicate automation handler names")
    void shouldRejectDuplicateAutomationHandlerNames() {
        List<AutomationHandler> handlers = List.of(handler("duplicate-name"), handler("duplicate-name"));

        assertThatThrownBy(() -> autoConfiguration.automationHandlers(providerOf(handlers)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate AutomationHandler names found")
                .hasMessageContaining("duplicate-name");
    }

    @Test
    @DisplayName("Should build processor configs when handler names are distinct")
    void shouldBuildProcessorConfigsWhenHandlerNamesAreDistinct() {
        Map<String, AutomationHandler> handlers = Map.of(
                "automation-a", handler("automation-a"),
                "automation-b", handler("automation-b"));

        Map<String, AutomationProcessorConfig> configs = autoConfiguration.automationProcessorConfigs(
                new AutomationsConfig(), handlers);

        assertThat(configs).containsKeys("automation-a", "automation-b");
    }

    @Test
    @DisplayName("Should reject automation handler when CommandExecutor is absent")
    void shouldRejectAutomationHandlerWhenCommandExecutorIsAbsent() {
        Map<String, AutomationHandler> handlers = Map.of("automation", handler("automation"));

        assertThatThrownBy(() -> autoConfiguration.automationEventHandler(
                handlers,
                emptyProvider(),
                mock(ApplicationEventPublisher.class),
                mock(ClockProvider.class),
                CircuitBreakerRegistry.ofDefaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AutomationHandlers require a CommandExecutor bean")
                .hasMessageContaining("automation");
    }

    @Test
    @DisplayName("Should create automation event handler when CommandExecutor is present")
    void shouldCreateAutomationEventHandlerWhenCommandExecutorIsPresent() {
        Map<String, AutomationHandler> handlers = Map.of("automation", handler("automation"));

        EventHandler<String> eventHandler = autoConfiguration.automationEventHandler(
                handlers,
                providerOfValue(mock(CommandExecutor.class)),
                mock(ApplicationEventPublisher.class),
                mock(ClockProvider.class),
                CircuitBreakerRegistry.ofDefaults());

        assertThat(eventHandler).isNotNull();
    }

    @Test
    @DisplayName("Should create legacy automation event processor with fallback options")
    void shouldCreateLegacyAutomationEventProcessorWithFallbackOptions() {
        InstanceIdProvider instanceIdProvider = mock(InstanceIdProvider.class);
        when(instanceIdProvider.getInstanceId()).thenReturn("instance-1");

        EventProcessor<AutomationProcessorConfig, String> processor = autoConfiguration.automationsEventProcessor(
                Map.of("automation", new AutomationProcessorConfig("automation", new AutomationsConfig(), handler("automation"))),
                mock(ProgressTracker.class),
                (processorId, lastPosition, batchSize) -> List.of(),
                (processorId, events) -> 0,
                instanceIdProvider,
                new WriteDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class),
                Optional.empty(),
                Optional.empty());

        assertThat(processor).isNotNull();
    }

    @Test
    @DisplayName("Should create shared-fetch automation event processor")
    void shouldCreateSharedFetchAutomationEventProcessor() {
        AutomationsConfig config = new AutomationsConfig();
        config.setFetchBatchSize(25);
        AutomationHandler handler = handler("automation");
        Map<String, AutomationHandler> handlers = Map.of("automation", handler);
        Map<String, AutomationProcessorConfig> processorConfigs = Map.of(
                "automation", new AutomationProcessorConfig("automation", config, handler));
        InstanceIdProvider instanceIdProvider = mock(InstanceIdProvider.class);
        when(instanceIdProvider.getInstanceId()).thenReturn("instance-1");

        EventProcessor<AutomationProcessorConfig, String> processor = autoConfiguration.automationsEventProcessorSharedFetch(
                processorConfigs,
                handlers,
                mock(ProgressTracker.class),
                (processorId, events) -> 0,
                instanceIdProvider,
                config,
                new WriteDataSource(mock(DataSource.class)),
                new ReadDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class));

        assertThat(processor).isInstanceOf(SharedFetchModuleProcessor.class);
    }

    private static AutomationHandler handler(String name) {
        return new AutomationHandler() {
            @Override public String getAutomationName() { return name; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public List<AutomationDecision> decide(StoredEvent event) {
                return List.of();
            }
        };
    }

    private static <T> ObjectProvider<List<T>> providerOf(List<T> value) {
        return new ObjectProvider<>() {
            @Override public List<T> getObject(@Nullable Object... args) { return value; }
            @Override public List<T> getIfAvailable() { return value; }
            @Override public List<T> getIfUnique() { return value; }
            @Override public List<T> getObject() { return value; }
        };
    }

    private static <T> ObjectProvider<T> providerOfValue(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(@Nullable Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getObject() { return value; }
        };
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override public T getObject(@Nullable Object... args) {
                throw new NoSuchBeanDefinitionException(Object.class);
            }
            @Override public @Nullable T getIfAvailable() { return null; }
            @Override public @Nullable T getIfUnique() { return null; }
            @Override public T getObject() {
                throw new NoSuchBeanDefinitionException(Object.class);
            }
        };
    }
}
