package com.crablet.eventpoller.wakeup;

import org.jspecify.annotations.NonNull;

/**
 * Default wakeup source factory used when notifications are disabled.
 */
public final class NoopProcessorWakeupSourceFactory implements ProcessorWakeupSourceFactory {

    @Override
    public @NonNull ProcessorWakeupSource create() {
        return new NoopProcessorWakeupSource();
    }
}
