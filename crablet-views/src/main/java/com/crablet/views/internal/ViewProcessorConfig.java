package com.crablet.views.internal;

import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.processor.ProcessorRuntimeOverrideResolver;
import com.crablet.views.ViewSubscription;
import com.crablet.views.config.ViewsConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that creates ProcessorConfig instances from ViewsConfig and ViewSubscription.
 * Creates one ProcessorConfig per view subscription.
 */
public class ViewProcessorConfig implements ProcessorConfig<String> {
    
    private final String viewName;
    private final ViewsConfig viewsConfig;
    private final ViewSubscription subscriptionConfig;
    
    public ViewProcessorConfig(
            String viewName,
            ViewsConfig viewsConfig,
            ViewSubscription subscriptionConfig) {
        this.viewName = viewName;
        this.viewsConfig = viewsConfig;
        this.subscriptionConfig = subscriptionConfig;
    }
    
    @Override
    public String getProcessorId() {
        return viewName;
    }
    
    @Override
    public long getPollingIntervalMs() {
        return ProcessorRuntimeOverrideResolver.pollingIntervalMs(subscriptionConfig, viewsConfig.getPollingIntervalMs());
    }

    @Override
    public int getBatchSize() {
        return ProcessorRuntimeOverrideResolver.batchSize(subscriptionConfig, viewsConfig.getBatchSize());
    }

    @Override
    public boolean isBackoffEnabled() {
        return ProcessorRuntimeOverrideResolver.backoffEnabled(subscriptionConfig, true);
    }

    @Override
    public int getBackoffThreshold() {
        return ProcessorRuntimeOverrideResolver.backoffThreshold(subscriptionConfig, viewsConfig.getBackoffThreshold());
    }

    @Override
    public int getBackoffMultiplier() {
        return ProcessorRuntimeOverrideResolver.backoffMultiplier(subscriptionConfig, viewsConfig.getBackoffMultiplier());
    }

    @Override
    public int getBackoffMaxSeconds() {
        return ProcessorRuntimeOverrideResolver.backoffMaxSeconds(subscriptionConfig, viewsConfig.getMaxBackoffSeconds());
    }

    @Override
    public long getLeaderElectionRetryIntervalMs() {
        return viewsConfig.getLeaderElectionRetryIntervalMs();
    }
    
    @Override
    public boolean isEnabled() {
        return viewsConfig.isEnabled();
    }
    
    public ViewSubscription getSubscriptionConfig() {
        return subscriptionConfig;
    }
    
    /**
     * Create a map of all view names to their ProcessorConfig instances.
     * <p>
     * Uses subscription.getViewName() as the key to ensure consistency,
     * regardless of how the subscriptions map is keyed (bean names vs view names).
     */
    public static Map<String, ViewProcessorConfig> createConfigMap(
            ViewsConfig viewsConfig,
            Map<String, ViewSubscription> subscriptions) {
        
        Map<String, ViewProcessorConfig> configs = new HashMap<>();
        
        for (ViewSubscription subscriptionConfig : subscriptions.values()) {
            // Always use view name from subscription, not the map key
            // This ensures consistency even if Spring auto-wires Map with bean names as keys
            String viewName = subscriptionConfig.getViewName();
            
            ViewProcessorConfig config = new ViewProcessorConfig(
                viewName, viewsConfig, subscriptionConfig);
            configs.put(viewName, config);
        }
        
        return configs;
    }
}
