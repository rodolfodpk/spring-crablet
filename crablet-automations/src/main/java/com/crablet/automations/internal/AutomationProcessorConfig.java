package com.crablet.automations.internal;

import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.config.AutomationsConfig;
import com.crablet.eventpoller.processor.ProcessorConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that creates {@link ProcessorConfig} instances from {@link AutomationsConfig}
 * and {@link AutomationSubscription} definitions. One config per automation.
 */
public class AutomationProcessorConfig implements ProcessorConfig<String> {

    private final String automationName;
    private final AutomationsConfig automationsConfig;
    private final AutomationSubscription subscription;

    public AutomationProcessorConfig(String automationName, AutomationsConfig automationsConfig,
                                     AutomationSubscription subscription) {
        this.automationName = automationName;
        this.automationsConfig = automationsConfig;
        this.subscription = subscription;
    }

    @Override
    public String getProcessorId() { return automationName; }

    @Override
    public long getPollingIntervalMs() {
        Long override = subscription.getPollingIntervalMs();
        return override != null ? override : automationsConfig.getPollingIntervalMs();
    }

    @Override
    public int getBatchSize() {
        Integer override = subscription.getBatchSize();
        return override != null ? override : automationsConfig.getBatchSize();
    }

    @Override
    public boolean isBackoffEnabled() {
        Boolean override = subscription.getBackoffEnabled();
        return override != null ? override : true;
    }

    @Override
    public int getBackoffThreshold() {
        Integer override = subscription.getBackoffThreshold();
        return override != null ? override : automationsConfig.getBackoffThreshold();
    }

    @Override
    public int getBackoffMultiplier() {
        Integer override = subscription.getBackoffMultiplier();
        return override != null ? override : automationsConfig.getBackoffMultiplier();
    }

    @Override
    public int getBackoffMaxSeconds() {
        Integer override = subscription.getBackoffMaxSeconds();
        return override != null ? override : automationsConfig.getMaxBackoffSeconds();
    }

    @Override
    public long getLeaderElectionRetryIntervalMs() { return automationsConfig.getLeaderElectionRetryIntervalMs(); }

    @Override
    public boolean isEnabled() { return automationsConfig.isEnabled(); }

    public static Map<String, AutomationProcessorConfig> createConfigMap(
            AutomationsConfig automationsConfig,
            Map<String, AutomationSubscription> subscriptions) {

        Map<String, AutomationProcessorConfig> configs = new HashMap<>();
        for (AutomationSubscription subscription : subscriptions.values()) {
            String name = subscription.getAutomationName();
            configs.put(name, new AutomationProcessorConfig(name, automationsConfig, subscription));
        }
        return configs;
    }
}
