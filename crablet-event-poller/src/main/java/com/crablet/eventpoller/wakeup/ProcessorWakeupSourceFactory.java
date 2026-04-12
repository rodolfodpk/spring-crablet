package com.crablet.eventpoller.wakeup;

/**
 * Factory for per-processor wakeup source instances.
 */
public interface ProcessorWakeupSourceFactory {

    ProcessorWakeupSource create();
}
