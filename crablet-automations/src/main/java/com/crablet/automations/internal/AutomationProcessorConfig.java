package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.config.AutomationsConfig;
import com.crablet.eventpoller.processor.ProcessorConfig;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that creates {@link ProcessorConfig} instances from {@link AutomationsConfig}
 * and {@link AutomationSubscription} definitions. One config per automation.
 * In-process handlers (no subscription) use global config only.
 */
public class AutomationProcessorConfig implements ProcessorConfig<String> {

    private final String automationName;
    private final AutomationsConfig automationsConfig;
    private final AutomationSubscription subscription;

    public AutomationProcessorConfig(String automationName, AutomationsConfig automationsConfig,
                                     @Nullable AutomationSubscription subscription) {
        this.automationName = automationName;
        this.automationsConfig = automationsConfig;
        this.subscription = subscription;
    }

    @Override
    public String getProcessorId() { return automationName; }

    @Override
    public long getPollingIntervalMs() {
        if (subscription != null) {
            Long override = subscription.getPollingIntervalMs();
            if (override != null) return override;
        }
        return automationsConfig.getPollingIntervalMs();
    }

    @Override
    public int getBatchSize() {
        if (subscription != null) {
            Integer override = subscription.getBatchSize();
            if (override != null) return override;
        }
        return automationsConfig.getBatchSize();
    }

    @Override
    public boolean isBackoffEnabled() {
        if (subscription != null) {
            Boolean override = subscription.getBackoffEnabled();
            if (override != null) return override;
        }
        return true;
    }

    @Override
    public int getBackoffThreshold() {
        if (subscription != null) {
            Integer override = subscription.getBackoffThreshold();
            if (override != null) return override;
        }
        return automationsConfig.getBackoffThreshold();
    }

    @Override
    public int getBackoffMultiplier() {
        if (subscription != null) {
            Integer override = subscription.getBackoffMultiplier();
            if (override != null) return override;
        }
        return automationsConfig.getBackoffMultiplier();
    }

    @Override
    public int getBackoffMaxSeconds() {
        if (subscription != null) {
            Integer override = subscription.getBackoffMaxSeconds();
            if (override != null) return override;
        }
        return automationsConfig.getMaxBackoffSeconds();
    }

    @Override
    public long getLeaderElectionRetryIntervalMs() { return automationsConfig.getLeaderElectionRetryIntervalMs(); }

    @Override
    public boolean isEnabled() { return automationsConfig.isEnabled(); }

    public static Map<String, AutomationProcessorConfig> createConfigMap(
            AutomationsConfig automationsConfig,
            Map<String, AutomationSubscription> subscriptions,
            Map<String, AutomationHandler> inProcessHandlers) {

        Map<String, AutomationProcessorConfig> configs = new HashMap<>();
        for (AutomationSubscription subscription : subscriptions.values()) {
            String name = subscription.getAutomationName();
            configs.put(name, new AutomationProcessorConfig(name, automationsConfig, subscription));
        }
        for (String name : inProcessHandlers.keySet()) {
            // In-process handlers use global config only (no per-instance overrides)
            configs.put(name, new AutomationProcessorConfig(name, automationsConfig, null));
        }
        return configs;
    }
}
