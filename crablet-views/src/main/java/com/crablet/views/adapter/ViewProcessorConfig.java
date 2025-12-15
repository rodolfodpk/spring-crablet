package com.crablet.views.adapter;

import com.crablet.eventprocessor.processor.ProcessorConfig;
import com.crablet.views.config.ViewSubscriptionConfig;
import com.crablet.views.config.ViewsConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that creates ProcessorConfig instances from ViewsConfig and ViewSubscriptionConfig.
 * Creates one ProcessorConfig per view subscription.
 */
public class ViewProcessorConfig implements ProcessorConfig<String> {
    
    private final String viewName;
    private final ViewsConfig viewsConfig;
    private final ViewSubscriptionConfig subscriptionConfig;
    
    public ViewProcessorConfig(
            String viewName,
            ViewsConfig viewsConfig,
            ViewSubscriptionConfig subscriptionConfig) {
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
        return viewsConfig.getPollingIntervalMs();
    }
    
    @Override
    public int getBatchSize() {
        return viewsConfig.getBatchSize();
    }
    
    @Override
    public boolean isBackoffEnabled() {
        return true; // Always enabled for views
    }
    
    @Override
    public int getBackoffThreshold() {
        return viewsConfig.getBackoffThreshold();
    }
    
    @Override
    public int getBackoffMultiplier() {
        return viewsConfig.getBackoffMultiplier();
    }
    
    @Override
    public int getBackoffMaxSeconds() {
        return viewsConfig.getMaxBackoffSeconds();
    }
    
    @Override
    public boolean isEnabled() {
        return viewsConfig.isEnabled();
    }
    
    public ViewSubscriptionConfig getSubscriptionConfig() {
        return subscriptionConfig;
    }
    
    /**
     * Create a map of all view names to their ProcessorConfig instances.
     */
    public static Map<String, ViewProcessorConfig> createConfigMap(
            ViewsConfig viewsConfig,
            Map<String, ViewSubscriptionConfig> subscriptions) {
        
        Map<String, ViewProcessorConfig> configs = new HashMap<>();
        
        for (var entry : subscriptions.entrySet()) {
            String viewName = entry.getKey();
            ViewSubscriptionConfig subscriptionConfig = entry.getValue();
            
            ViewProcessorConfig config = new ViewProcessorConfig(
                viewName, viewsConfig, subscriptionConfig);
            configs.put(viewName, config);
        }
        
        return configs;
    }
}

