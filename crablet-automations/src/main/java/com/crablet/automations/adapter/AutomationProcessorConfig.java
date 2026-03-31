package com.crablet.automations.adapter;

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

    public AutomationProcessorConfig(String automationName, AutomationsConfig automationsConfig) {
        this.automationName = automationName;
        this.automationsConfig = automationsConfig;
    }

    @Override
    public String getProcessorId() { return automationName; }

    @Override
    public long getPollingIntervalMs() { return automationsConfig.getPollingIntervalMs(); }

    @Override
    public int getBatchSize() { return automationsConfig.getBatchSize(); }

    @Override
    public boolean isBackoffEnabled() { return true; }

    @Override
    public int getBackoffThreshold() { return automationsConfig.getBackoffThreshold(); }

    @Override
    public int getBackoffMultiplier() { return automationsConfig.getBackoffMultiplier(); }

    @Override
    public int getBackoffMaxSeconds() { return automationsConfig.getMaxBackoffSeconds(); }

    @Override
    public boolean isEnabled() { return automationsConfig.isEnabled(); }

    public static Map<String, AutomationProcessorConfig> createConfigMap(
            AutomationsConfig automationsConfig,
            Map<String, AutomationSubscription> subscriptions) {

        Map<String, AutomationProcessorConfig> configs = new HashMap<>();
        for (AutomationSubscription subscription : subscriptions.values()) {
            String name = subscription.getAutomationName();
            configs.put(name, new AutomationProcessorConfig(name, automationsConfig));
        }
        return configs;
    }
}
