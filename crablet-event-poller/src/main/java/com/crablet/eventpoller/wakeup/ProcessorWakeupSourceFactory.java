package com.crablet.eventpoller.wakeup;

import org.jspecify.annotations.NonNull;

/**
 * Factory for per-processor wakeup source instances.
 */
public interface ProcessorWakeupSourceFactory {

    @NonNull ProcessorWakeupSource create();
}
