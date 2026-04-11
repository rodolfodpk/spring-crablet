package com.crablet.automations.config;

import com.crablet.automations.AutomationHandler;
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
                "webhook-automation", webhookHandler("webhook-automation"),
                "in-process-automation", handler("in-process-automation"));

        Map<String, AutomationProcessorConfig> configs = autoConfiguration.automationProcessorConfigs(
                new AutomationsConfig(), handlers);

        assertThat(configs).containsKeys("webhook-automation", "in-process-automation");
    }

    private static AutomationHandler handler(String name) {
        return new AutomationHandler() {
            @Override public String getAutomationName() { return name; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor commandExecutor) {
            }
        };
    }

    private static AutomationHandler webhookHandler(String name) {
        return new AutomationHandler() {
            @Override public String getAutomationName() { return name; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public String getWebhookUrl() { return "http://localhost/webhook"; }
        };
    }

    private static <T> ObjectProvider<List<T>> providerOf(List<T> value) {
        return new ObjectProvider<>() {
            @Override public List<T> getObject(Object... args) { return value; }
            @Override public List<T> getIfAvailable() { return value; }
            @Override public List<T> getIfUnique() { return value; }
            @Override public List<T> getObject() { return value; }
        };
    }
}
