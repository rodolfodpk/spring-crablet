package com.crablet.eventpoller.wakeup;

import org.jspecify.annotations.NonNull;

/**
 * Default wakeup source used when notifications are disabled.
 */
public final class NoopProcessorWakeupSource implements ProcessorWakeupSource {

    @Override
    public void start(@NonNull Runnable onWakeup) {
    }

    @Override
    public void close() {
    }
}
