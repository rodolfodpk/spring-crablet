package com.crablet.eventpoller.wakeup;

import org.jspecify.annotations.NonNull;

/**
 * Best-effort wakeup source that can trigger an immediate poll.
 */
public interface ProcessorWakeupSource extends AutoCloseable {

    void start(@NonNull Runnable onWakeup);

    @Override
    void close();
}
