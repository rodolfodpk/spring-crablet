package com.crablet.eventpoller.wakeup;

/**
 * Best-effort wakeup source that can trigger an immediate poll.
 */
public interface ProcessorWakeupSource extends AutoCloseable {

    void start(Runnable onWakeup);

    @Override
    void close();
}
