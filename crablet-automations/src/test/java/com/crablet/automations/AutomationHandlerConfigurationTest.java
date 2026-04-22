package com.crablet.automations;

import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AutomationHandler Configuration Tests")
class AutomationHandlerConfigurationTest {

    @Test
    @DisplayName("Default runtime override configuration should be absent")
    void defaultRuntimeOverrideConfigurationShouldBeAbsent() {
        AutomationHandler handler = baseHandler();

        assertThat(handler.getPollingIntervalMs()).isNull();
        assertThat(handler.getBatchSize()).isNull();
        assertThat(handler.getBackoffEnabled()).isNull();
        assertThat(handler.getBackoffThreshold()).isNull();
        assertThat(handler.getBackoffMultiplier()).isNull();
        assertThat(handler.getBackoffMaxSeconds()).isNull();
    }

    @Test
    @DisplayName("Handler can override runtime settings")
    void handlerCanOverrideRuntimeSettings() {
        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "handler"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public Long getPollingIntervalMs() { return 2000L; }
            @Override public Integer getBatchSize() { return 25; }
            @Override public Boolean getBackoffEnabled() { return false; }
            @Override public Integer getBackoffThreshold() { return 3; }
            @Override public Integer getBackoffMultiplier() { return 4; }
            @Override public Integer getBackoffMaxSeconds() { return 90; }
            @Override public List<AutomationDecision> decide(StoredEvent event) {
                return List.of();
            }
        };

        assertThat(handler.getPollingIntervalMs()).isEqualTo(2000L);
        assertThat(handler.getBatchSize()).isEqualTo(25);
        assertThat(handler.getBackoffEnabled()).isFalse();
        assertThat(handler.getBackoffThreshold()).isEqualTo(3);
        assertThat(handler.getBackoffMultiplier()).isEqualTo(4);
        assertThat(handler.getBackoffMaxSeconds()).isEqualTo(90);
    }

    private AutomationHandler baseHandler() {
        return new AutomationHandler() {
            @Override public String getAutomationName() { return "test"; }
            @Override public Set<String> getEventTypes() { return Set.of("SomeEvent"); }
            @Override public List<AutomationDecision> decide(StoredEvent event) {
                return List.of();
            }
        };
    }
}
