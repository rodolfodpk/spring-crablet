package com.crablet.automations.adapter;

import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.config.AutomationsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AutomationProcessorConfig}.
 */
@DisplayName("AutomationProcessorConfig Unit Tests")
class AutomationProcessorConfigTest {

    @Test
    @DisplayName("Should use automation name as processor ID")
    void shouldUseAutomationName_AsProcessorId() {
        // Given
        AutomationsConfig config = createDefaultConfig();

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("wallet-notification", config);

        // Then
        assertThat(processorConfig.getProcessorId()).isEqualTo("wallet-notification");
    }

    @Test
    @DisplayName("Should delegate pollingIntervalMs from AutomationsConfig")
    void shouldDelegatePollingIntervalMs_FromAutomationsConfig() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setPollingIntervalMs(2000L);

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.getPollingIntervalMs()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("Should delegate batchSize from AutomationsConfig")
    void shouldDelegateBatchSize_FromAutomationsConfig() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setBatchSize(50);

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.getBatchSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should always return true for isBackoffEnabled")
    void shouldAlwaysReturnTrue_ForIsBackoffEnabled() {
        // Given
        AutomationsConfig config = createDefaultConfig();

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.isBackoffEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should delegate backoffThreshold from AutomationsConfig")
    void shouldDelegateBackoffThreshold_FromAutomationsConfig() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setBackoffThreshold(5);

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.getBackoffThreshold()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should delegate backoffMultiplier from AutomationsConfig")
    void shouldDelegateBackoffMultiplier_FromAutomationsConfig() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setBackoffMultiplier(3);

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.getBackoffMultiplier()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should delegate backoffMaxSeconds from AutomationsConfig")
    void shouldDelegateBackoffMaxSeconds_FromAutomationsConfig() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setMaxBackoffSeconds(120);

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.getBackoffMaxSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("Should delegate isEnabled from AutomationsConfig")
    void shouldDelegateIsEnabled_FromAutomationsConfig() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setEnabled(true);

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should handle disabled AutomationsConfig")
    void shouldHandleDisabledAutomationsConfig() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setEnabled(false);

        // When
        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config);

        // Then
        assertThat(processorConfig.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should create config map for all subscriptions")
    void shouldCreateConfigMap_ForAllSubscriptions() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        Map<String, AutomationSubscription> subscriptions = new HashMap<>();
        subscriptions.put("wallet-notification", AutomationSubscription.builder("wallet-notification").build());
        subscriptions.put("order-fulfillment", AutomationSubscription.builder("order-fulfillment").build());

        // When
        Map<String, AutomationProcessorConfig> configs = AutomationProcessorConfig.createConfigMap(config, subscriptions);

        // Then
        assertThat(configs).hasSize(2);
        assertThat(configs).containsKey("wallet-notification");
        assertThat(configs).containsKey("order-fulfillment");
        assertThat(configs.get("wallet-notification").getProcessorId()).isEqualTo("wallet-notification");
        assertThat(configs.get("order-fulfillment").getProcessorId()).isEqualTo("order-fulfillment");
    }

    @Test
    @DisplayName("Should create empty config map for empty subscriptions")
    void shouldCreateEmptyConfigMap_ForEmptySubscriptions() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        Map<String, AutomationSubscription> subscriptions = new HashMap<>();

        // When
        Map<String, AutomationProcessorConfig> configs = AutomationProcessorConfig.createConfigMap(config, subscriptions);

        // Then
        assertThat(configs).isEmpty();
    }

    @Test
    @DisplayName("Should propagate global config to all entries in config map")
    void shouldPropagateGlobalConfig_ToAllEntriesInConfigMap() {
        // Given
        AutomationsConfig config = createDefaultConfig();
        config.setPollingIntervalMs(3000L);
        config.setBatchSize(75);
        Map<String, AutomationSubscription> subscriptions = new HashMap<>();
        subscriptions.put("automation-a", AutomationSubscription.builder("automation-a").build());
        subscriptions.put("automation-b", AutomationSubscription.builder("automation-b").build());

        // When
        Map<String, AutomationProcessorConfig> configs = AutomationProcessorConfig.createConfigMap(config, subscriptions);

        // Then
        assertThat(configs.get("automation-a").getPollingIntervalMs()).isEqualTo(3000L);
        assertThat(configs.get("automation-a").getBatchSize()).isEqualTo(75);
        assertThat(configs.get("automation-b").getPollingIntervalMs()).isEqualTo(3000L);
        assertThat(configs.get("automation-b").getBatchSize()).isEqualTo(75);
    }

    private AutomationsConfig createDefaultConfig() {
        AutomationsConfig config = new AutomationsConfig();
        config.setEnabled(true);
        config.setPollingIntervalMs(1000L);
        config.setBatchSize(100);
        config.setBackoffThreshold(10);
        config.setBackoffMultiplier(2);
        config.setMaxBackoffSeconds(60);
        return config;
    }
}
