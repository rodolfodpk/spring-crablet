package com.crablet.automations.config;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.internal.AutomationProcessorConfig;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AutomationsAutoConfiguration Unit Tests")
class AutomationsAutoConfigurationTest {

    private final AutomationsAutoConfiguration autoConfiguration = new AutomationsAutoConfiguration();

    @Test
    @DisplayName("Should reject duplicate automation subscription names")
    void shouldRejectDuplicateAutomationSubscriptionNames() {
        List<AutomationSubscription> subscriptions = List.of(
                AutomationSubscription.builder("duplicate-name").webhookUrl("http://localhost/a").build(),
                AutomationSubscription.builder("duplicate-name").webhookUrl("http://localhost/b").build()
        );

        assertThatThrownBy(() -> autoConfiguration.automationSubscriptions(providerOf(subscriptions)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate AutomationSubscription names found")
                .hasMessageContaining("duplicate-name");
    }

    @Test
    @DisplayName("Should reject duplicate automation handler names")
    void shouldRejectDuplicateAutomationHandlerNames() {
        List<AutomationHandler> handlers = List.of(
                handler("duplicate-name"),
                handler("duplicate-name")
        );

        assertThatThrownBy(() -> autoConfiguration.inProcessAutomationHandlers(providerOf(handlers)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate AutomationHandler names found")
                .hasMessageContaining("duplicate-name");
    }

    @Test
    @DisplayName("Should reject overlapping subscription and handler names")
    void shouldRejectOverlappingSubscriptionAndHandlerNames() {
        Map<String, AutomationSubscription> subscriptions = Map.of(
                "shared-name",
                AutomationSubscription.builder("shared-name").webhookUrl("http://localhost/webhook").build()
        );
        Map<String, AutomationHandler> handlers = Map.of("shared-name", handler("shared-name"));

        assertThatThrownBy(() -> autoConfiguration.automationProcessorConfigs(
                new AutomationsConfig(), subscriptions, handlers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Automation names cannot be registered as both")
                .hasMessageContaining("shared-name");
    }

    @Test
    @DisplayName("Should build processor configs when subscription and handler names are distinct")
    void shouldBuildProcessorConfigsWhenNamesAreDistinct() {
        Map<String, AutomationSubscription> subscriptions = Map.of(
                "webhook-automation",
                AutomationSubscription.builder("webhook-automation").webhookUrl("http://localhost/webhook").build()
        );
        Map<String, AutomationHandler> handlers = Map.of("in-process-automation", handler("in-process-automation"));

        Map<String, AutomationProcessorConfig> configs = autoConfiguration.automationProcessorConfigs(
                new AutomationsConfig(), subscriptions, handlers);

        assertThat(configs).containsKeys("webhook-automation", "in-process-automation");
    }

    private static AutomationHandler handler(String name) {
        return new AutomationHandler() {
            @Override
            public String getAutomationName() {
                return name;
            }

            @Override
            public Set<String> getEventTypes() {
                return Set.of("WalletOpened");
            }

            @Override
            public void react(StoredEvent event, CommandExecutor commandExecutor) {
            }
        };
    }

    private static <T> ObjectProvider<List<T>> providerOf(List<T> value) {
        return new ObjectProvider<>() {
            @Override
            public List<T> getObject(Object... args) {
                return value;
            }

            @Override
            public List<T> getIfAvailable() {
                return value;
            }

            @Override
            public List<T> getIfUnique() {
                return value;
            }

            @Override
            public List<T> getObject() {
                return value;
            }
        };
    }
}
