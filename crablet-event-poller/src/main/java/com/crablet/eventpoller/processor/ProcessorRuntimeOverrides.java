package com.crablet.eventpoller.processor;

import org.jspecify.annotations.Nullable;

/**
 * Optional per-processor runtime overrides.
 *
 * <p>Implement this on module-level processor definitions to override global poller
 * defaults for a specific processor instance.
 */
public interface ProcessorRuntimeOverrides {

    default @Nullable Long getPollingIntervalMs() {
        return null;
    }

    default @Nullable Integer getBatchSize() {
        return null;
    }

    default @Nullable Boolean getBackoffEnabled() {
        return null;
    }

    default @Nullable Integer getBackoffThreshold() {
        return null;
    }

    default @Nullable Integer getBackoffMultiplier() {
        return null;
    }

    default @Nullable Integer getBackoffMaxSeconds() {
        return null;
    }
}
