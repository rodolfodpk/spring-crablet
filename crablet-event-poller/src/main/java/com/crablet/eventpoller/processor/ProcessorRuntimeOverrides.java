package com.crablet.eventpoller.processor;

/**
 * Optional per-processor runtime overrides.
 *
 * <p>Implement this on module-level processor definitions to override global poller
 * defaults for a specific processor instance.
 */
public interface ProcessorRuntimeOverrides {

    default Long getPollingIntervalMs() {
        return null;
    }

    default Integer getBatchSize() {
        return null;
    }

    default Boolean getBackoffEnabled() {
        return null;
    }

    default Integer getBackoffThreshold() {
        return null;
    }

    default Integer getBackoffMultiplier() {
        return null;
    }

    default Integer getBackoffMaxSeconds() {
        return null;
    }
}
