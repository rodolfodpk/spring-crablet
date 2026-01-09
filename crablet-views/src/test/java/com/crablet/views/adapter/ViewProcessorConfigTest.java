package com.crablet.views.adapter;

import com.crablet.views.config.ViewSubscriptionConfig;
import com.crablet.views.config.ViewsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ViewProcessorConfig.
 * Tests configuration mapping, property delegation, and config map creation.
 */
@DisplayName("ViewProcessorConfig Unit Tests")
class ViewProcessorConfigTest {

    @Test
    @DisplayName("Should create config with view name")
    void shouldCreateConfig_WithViewName() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view")
                .build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getProcessorId()).isEqualTo("wallet-view");
    }

    @Test
    @DisplayName("Should delegate pollingIntervalMs from ViewsConfig")
    void shouldDelegatePollingIntervalMs_FromViewsConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setPollingIntervalMs(2000L);
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getPollingIntervalMs()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("Should delegate batchSize from ViewsConfig")
    void shouldDelegateBatchSize_FromViewsConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setBatchSize(50);
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getBatchSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should always return true for isBackoffEnabled")
    void shouldAlwaysReturnTrue_ForIsBackoffEnabled() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.isBackoffEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should delegate backoffThreshold from ViewsConfig")
    void shouldDelegateBackoffThreshold_FromViewsConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setBackoffThreshold(5);
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getBackoffThreshold()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should delegate backoffMultiplier from ViewsConfig")
    void shouldDelegateBackoffMultiplier_FromViewsConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setBackoffMultiplier(3);
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getBackoffMultiplier()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should delegate backoffMaxSeconds from ViewsConfig")
    void shouldDelegateBackoffMaxSeconds_FromViewsConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setMaxBackoffSeconds(120);
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getBackoffMaxSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("Should delegate isEnabled from ViewsConfig")
    void shouldDelegateIsEnabled_FromViewsConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setEnabled(true);
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should return subscription config")
    void shouldReturnSubscriptionConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view")
                .eventTypes("WalletOpened", "DepositMade")
                .build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getSubscriptionConfig()).isSameAs(subscriptionConfig);
    }

    @Test
    @DisplayName("Should create config map for all subscriptions")
    void shouldCreateConfigMap_ForAllSubscriptions() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        Map<String, ViewSubscriptionConfig> subscriptions = new HashMap<>();
        subscriptions.put("wallet-view", ViewSubscriptionConfig.builder("wallet-view").build());
        subscriptions.put("order-view", ViewSubscriptionConfig.builder("order-view").build());

        // When
        Map<String, ViewProcessorConfig> configs = ViewProcessorConfig.createConfigMap(viewsConfig, subscriptions);

        // Then
        assertThat(configs).hasSize(2);
        assertThat(configs).containsKey("wallet-view");
        assertThat(configs).containsKey("order-view");
        assertThat(configs.get("wallet-view").getProcessorId()).isEqualTo("wallet-view");
        assertThat(configs.get("order-view").getProcessorId()).isEqualTo("order-view");
    }

    @Test
    @DisplayName("Should create config map with empty subscriptions")
    void shouldCreateConfigMap_WithEmptySubscriptions() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        Map<String, ViewSubscriptionConfig> subscriptions = new HashMap<>();

        // When
        Map<String, ViewProcessorConfig> configs = ViewProcessorConfig.createConfigMap(viewsConfig, subscriptions);

        // Then
        assertThat(configs).isEmpty();
    }

    @Test
    @DisplayName("Should merge global and per-view config in createConfigMap")
    void shouldMergeGlobalAndPerViewConfig_InCreateConfigMap() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setPollingIntervalMs(2000L);
        viewsConfig.setBatchSize(50);
        Map<String, ViewSubscriptionConfig> subscriptions = new HashMap<>();
        subscriptions.put("wallet-view", ViewSubscriptionConfig.builder("wallet-view").build());

        // When
        Map<String, ViewProcessorConfig> configs = ViewProcessorConfig.createConfigMap(viewsConfig, subscriptions);

        // Then
        ViewProcessorConfig config = configs.get("wallet-view");
        assertThat(config.getPollingIntervalMs()).isEqualTo(2000L);
        assertThat(config.getBatchSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should handle multiple subscriptions with different configs")
    void shouldHandleMultipleSubscriptions_WithDifferentConfigs() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        Map<String, ViewSubscriptionConfig> subscriptions = new HashMap<>();
        subscriptions.put("wallet-view", ViewSubscriptionConfig.builder("wallet-view")
                .eventTypes("WalletOpened")
                .build());
        subscriptions.put("wallet-balance-view", ViewSubscriptionConfig.builder("wallet-balance-view")
                .eventTypes("DepositMade", "WithdrawalMade")
                .build());

        // When
        Map<String, ViewProcessorConfig> configs = ViewProcessorConfig.createConfigMap(viewsConfig, subscriptions);

        // Then
        assertThat(configs.get("wallet-view").getSubscriptionConfig().getEventTypes())
                .containsExactly("WalletOpened");
        assertThat(configs.get("wallet-balance-view").getSubscriptionConfig().getEventTypes())
                .containsExactlyInAnyOrder("DepositMade", "WithdrawalMade");
    }

    @Test
    @DisplayName("Should use view name as processor ID")
    void shouldUseViewName_AsProcessorId() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("custom-view-name")
                .build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("custom-view-name", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.getProcessorId()).isEqualTo("custom-view-name");
    }

    @Test
    @DisplayName("Should handle disabled views config")
    void shouldHandleDisabledViewsConfig() {
        // Given
        ViewsConfig viewsConfig = createDefaultViewsConfig();
        viewsConfig.setEnabled(false);
        ViewSubscriptionConfig subscriptionConfig = ViewSubscriptionConfig.builder("wallet-view").build();

        // When
        ViewProcessorConfig config = new ViewProcessorConfig("wallet-view", viewsConfig, subscriptionConfig);

        // Then
        assertThat(config.isEnabled()).isFalse();
    }

    private ViewsConfig createDefaultViewsConfig() {
        ViewsConfig config = new ViewsConfig();
        config.setEnabled(true);
        config.setPollingIntervalMs(1000L);
        config.setBatchSize(100);
        config.setBackoffThreshold(10);
        config.setBackoffMultiplier(2);
        config.setMaxBackoffSeconds(60);
        return config;
    }
}

