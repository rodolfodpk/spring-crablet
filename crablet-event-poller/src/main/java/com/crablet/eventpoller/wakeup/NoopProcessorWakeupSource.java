package com.crablet.eventpoller.wakeup;

/**
 * Default wakeup source used when notifications are disabled.
 */
public final class NoopProcessorWakeupSource implements ProcessorWakeupSource {

    @Override
    public void start(Runnable onWakeup) {
    }

    @Override
    public void close() {
    }
}
