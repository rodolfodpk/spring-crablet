package com.crablet.eventpoller.processor;

/**
 * Resolves optional per-processor overrides against module-level defaults.
 */
public final class ProcessorRuntimeOverrideResolver {

    private ProcessorRuntimeOverrideResolver() {}

    public static long pollingIntervalMs(ProcessorRuntimeOverrides overrides, long globalValue) {
        return overrides.getPollingIntervalMs() != null ? overrides.getPollingIntervalMs() : globalValue;
    }

    public static int batchSize(ProcessorRuntimeOverrides overrides, int globalValue) {
        return overrides.getBatchSize() != null ? overrides.getBatchSize() : globalValue;
    }

    public static boolean backoffEnabled(ProcessorRuntimeOverrides overrides, boolean globalValue) {
        return overrides.getBackoffEnabled() != null ? overrides.getBackoffEnabled() : globalValue;
    }

    public static int backoffThreshold(ProcessorRuntimeOverrides overrides, int globalValue) {
        return overrides.getBackoffThreshold() != null ? overrides.getBackoffThreshold() : globalValue;
    }

    public static int backoffMultiplier(ProcessorRuntimeOverrides overrides, int globalValue) {
        return overrides.getBackoffMultiplier() != null ? overrides.getBackoffMultiplier() : globalValue;
    }

    public static int backoffMaxSeconds(ProcessorRuntimeOverrides overrides, int globalValue) {
        return overrides.getBackoffMaxSeconds() != null ? overrides.getBackoffMaxSeconds() : globalValue;
    }
}
