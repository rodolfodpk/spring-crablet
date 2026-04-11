package com.crablet.views.internal;

import com.crablet.eventpoller.processor.ProcessorConfig;
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
        Long override = subscriptionConfig.getPollingIntervalMs();
        return override != null ? override : viewsConfig.getPollingIntervalMs();
    }

    @Override
    public int getBatchSize() {
        Integer override = subscriptionConfig.getBatchSize();
        return override != null ? override : viewsConfig.getBatchSize();
    }

    @Override
    public boolean isBackoffEnabled() {
        Boolean override = subscriptionConfig.getBackoffEnabled();
        return override != null ? override : true;
    }

    @Override
    public int getBackoffThreshold() {
        Integer override = subscriptionConfig.getBackoffThreshold();
        return override != null ? override : viewsConfig.getBackoffThreshold();
    }

    @Override
    public int getBackoffMultiplier() {
        Integer override = subscriptionConfig.getBackoffMultiplier();
        return override != null ? override : viewsConfig.getBackoffMultiplier();
    }

    @Override
    public int getBackoffMaxSeconds() {
        Integer override = subscriptionConfig.getBackoffMaxSeconds();
        return override != null ? override : viewsConfig.getMaxBackoffSeconds();
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
