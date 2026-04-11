package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.config.AutomationsConfig;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.processor.ProcessorRuntimeOverrideResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that creates {@link ProcessorConfig} instances from {@link AutomationsConfig}
 * and {@link AutomationHandler} definitions. One config per automation.
 */
public class AutomationProcessorConfig implements ProcessorConfig<String> {

    private final String automationName;
    private final AutomationsConfig automationsConfig;
    private final AutomationHandler handler;

    public AutomationProcessorConfig(String automationName, AutomationsConfig automationsConfig, AutomationHandler handler) {
        this.automationName = automationName;
        this.automationsConfig = automationsConfig;
        this.handler = handler;
    }

    @Override
    public String getProcessorId() { return automationName; }

    @Override
    public long getPollingIntervalMs() {
        return ProcessorRuntimeOverrideResolver.pollingIntervalMs(handler, automationsConfig.getPollingIntervalMs());
    }

    @Override
    public int getBatchSize() {
        return ProcessorRuntimeOverrideResolver.batchSize(handler, automationsConfig.getBatchSize());
    }

    @Override
    public boolean isBackoffEnabled() {
        return ProcessorRuntimeOverrideResolver.backoffEnabled(handler, true);
    }

    @Override
    public int getBackoffThreshold() {
        return ProcessorRuntimeOverrideResolver.backoffThreshold(handler, automationsConfig.getBackoffThreshold());
    }

    @Override
    public int getBackoffMultiplier() {
        return ProcessorRuntimeOverrideResolver.backoffMultiplier(handler, automationsConfig.getBackoffMultiplier());
    }

    @Override
    public int getBackoffMaxSeconds() {
        return ProcessorRuntimeOverrideResolver.backoffMaxSeconds(handler, automationsConfig.getMaxBackoffSeconds());
    }

    @Override
    public long getLeaderElectionRetryIntervalMs() { return automationsConfig.getLeaderElectionRetryIntervalMs(); }

    @Override
    public boolean isEnabled() { return automationsConfig.isEnabled(); }

    public static Map<String, AutomationProcessorConfig> createConfigMap(
            AutomationsConfig automationsConfig, Map<String, AutomationHandler> handlers) {

        Map<String, AutomationProcessorConfig> configs = new HashMap<>();
        for (AutomationHandler handler : handlers.values()) {
            String name = handler.getAutomationName();
            configs.put(name, new AutomationProcessorConfig(name, automationsConfig, handler));
        }
        return configs;
    }
}
