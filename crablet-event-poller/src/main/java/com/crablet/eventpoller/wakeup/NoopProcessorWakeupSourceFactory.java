package com.crablet.eventpoller.wakeup;

/**
 * Default wakeup source factory used when notifications are disabled.
 */
public final class NoopProcessorWakeupSourceFactory implements ProcessorWakeupSourceFactory {

    @Override
    public ProcessorWakeupSource create() {
        return new NoopProcessorWakeupSource();
    }
}
