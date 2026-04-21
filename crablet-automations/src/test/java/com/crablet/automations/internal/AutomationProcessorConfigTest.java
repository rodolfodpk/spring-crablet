package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.config.AutomationsConfig;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AutomationProcessorConfig}.
 */
@DisplayName("AutomationProcessorConfig Unit Tests")
class AutomationProcessorConfigTest {

    @Test
    @DisplayName("Should use automation name as processor ID")
    void shouldUseAutomationNameAsProcessorId() {
        AutomationsConfig config = createDefaultConfig();

        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("wallet-notification", config, noOverrides("wallet-notification"));

        assertThat(processorConfig.getProcessorId()).isEqualTo("wallet-notification");
    }

    @Test
    @DisplayName("Should delegate pollingIntervalMs from AutomationsConfig")
    void shouldDelegatePollingIntervalMsFromAutomationsConfig() {
        AutomationsConfig config = createDefaultConfig();
        config.setPollingIntervalMs(2000L);

        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config, noOverrides("automation"));

        assertThat(processorConfig.getPollingIntervalMs()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("Should use handler pollingIntervalMs override when present")
    void shouldUseHandlerPollingIntervalOverrideWhenPresent() {
        AutomationsConfig config = createDefaultConfig();
        AutomationHandler handler = new BaseHandler("automation") {
            @Override public Long getPollingIntervalMs() { return 5000L; }
        };

        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config, handler);

        assertThat(processorConfig.getPollingIntervalMs()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should delegate batchSize from AutomationsConfig")
    void shouldDelegateBatchSizeFromAutomationsConfig() {
        AutomationsConfig config = createDefaultConfig();
        config.setBatchSize(50);

        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config, noOverrides("automation"));

        assertThat(processorConfig.getBatchSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should use handler batchSize override when present")
    void shouldUseHandlerBatchSizeOverrideWhenPresent() {
        AutomationsConfig config = createDefaultConfig();
        AutomationHandler handler = new BaseHandler("automation") {
            @Override public Integer getBatchSize() { return 7; }
        };

        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config, handler);

        assertThat(processorConfig.getBatchSize()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should always return true for isBackoffEnabled when no override is set")
    void shouldAlwaysReturnTrueForIsBackoffEnabled() {
        AutomationsConfig config = createDefaultConfig();

        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config, noOverrides("automation"));

        assertThat(processorConfig.isBackoffEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should use handler backoffEnabled override when present")
    void shouldUseHandlerBackoffEnabledOverrideWhenPresent() {
        AutomationsConfig config = createDefaultConfig();
        AutomationHandler handler = new BaseHandler("automation") {
            @Override public Boolean getBackoffEnabled() { return false; }
        };

        AutomationProcessorConfig processorConfig = new AutomationProcessorConfig("automation", config, handler);

        assertThat(processorConfig.isBackoffEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should create config map for all handlers")
    void shouldCreateConfigMapForAllHandlers() {
        AutomationsConfig config = createDefaultConfig();
        Map<String, AutomationHandler> handlers = new HashMap<>();
        handlers.put("wallet-notification", noOverrides("wallet-notification"));
        handlers.put("order-fulfillment", noOverrides("order-fulfillment"));

        Map<String, AutomationProcessorConfig> configs = AutomationProcessorConfig.createConfigMap(config, handlers);

        assertThat(configs).hasSize(2);
        assertThat(configs).containsKeys("wallet-notification", "order-fulfillment");
    }

    @Test
    @DisplayName("Should create empty config map for empty handlers")
    void shouldCreateEmptyConfigMapForEmptyHandlers() {
        AutomationsConfig config = createDefaultConfig();

        Map<String, AutomationProcessorConfig> configs = AutomationProcessorConfig.createConfigMap(config, Map.of());

        assertThat(configs).isEmpty();
    }

    @Test
    @DisplayName("Should propagate global config to all entries in config map")
    void shouldPropagateGlobalConfigToAllEntriesInConfigMap() {
        AutomationsConfig config = createDefaultConfig();
        config.setPollingIntervalMs(3000L);
        config.setBatchSize(75);
        Map<String, AutomationHandler> handlers = new HashMap<>();
        handlers.put("automation-a", noOverrides("automation-a"));
        handlers.put("automation-b", noOverrides("automation-b"));

        Map<String, AutomationProcessorConfig> configs = AutomationProcessorConfig.createConfigMap(config, handlers);

        assertThat(configs.get("automation-a").getPollingIntervalMs()).isEqualTo(3000L);
        assertThat(configs.get("automation-a").getBatchSize()).isEqualTo(75);
        assertThat(configs.get("automation-b").getPollingIntervalMs()).isEqualTo(3000L);
        assertThat(configs.get("automation-b").getBatchSize()).isEqualTo(75);
    }

    @Test
    @DisplayName("Should expose shared-fetch configuration properties")
    void shouldExposeSharedFetchConfigurationProperties() {
        AutomationsConfig config = createDefaultConfig();
        AutomationsConfig.SharedFetch sharedFetch = new AutomationsConfig.SharedFetch();
        sharedFetch.setEnabled(true);

        config.setFetchBatchSize(250);
        config.setMaxErrors(4);
        config.setLeaderElectionRetryIntervalMs(15000L);
        config.setSharedFetch(sharedFetch);

        assertThat(config.getFetchBatchSize()).isEqualTo(250);
        assertThat(config.getMaxErrors()).isEqualTo(4);
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(15000L);
        assertThat(config.getSharedFetch()).isSameAs(sharedFetch);
        assertThat(config.getSharedFetch().isEnabled()).isTrue();
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

    private AutomationHandler noOverrides(String name) {
        return new BaseHandler(name);
    }

    private static class BaseHandler implements AutomationHandler {
        private final String name;

        private BaseHandler(String name) {
            this.name = name;
        }

        @Override public String getAutomationName() { return name; }
        @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
        @Override public void react(StoredEvent event, CommandExecutor commandExecutor) {
        }
    }
}
