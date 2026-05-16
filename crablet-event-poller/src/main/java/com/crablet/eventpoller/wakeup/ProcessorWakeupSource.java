package com.crablet.eventpoller.wakeup;


import java.util.Set;

/**
 * Best-effort wakeup source that can trigger an immediate poll.
 *
 * <p>Implementations may be shared across modules (singleton). Callers register
 * with {@link #start(Runnable)} or {@link #start(Set, Runnable)} and unregister
 * with {@link #close(Runnable)}; the underlying connection is kept alive as long
 * as at least one subscriber is registered.
 */
public interface ProcessorWakeupSource extends AutoCloseable {

    /** Register a subscriber (wildcard — woken on every notification). */
    void start(Runnable onWakeup);

    /**
     * Register a subscriber with a declared set of event type names.
     * An empty set means "subscribe to all types" (same as {@link #start(Runnable)}).
     * The subscriber is woken only when a notification carries at least one matching
     * type, or when the notification payload is a wildcard.
     */
    default void start(Set<String> subscribedEventTypes, Runnable onWakeup) {
        start(onWakeup);
    }

    /** Unregister a subscriber. Stops the underlying connection when the last subscriber leaves. */
    void close(Runnable onWakeup);

    /** Unregister a typed subscriber (delegates to {@link #close(Runnable)} by default). */
    default void close(Set<String> subscribedEventTypes, Runnable onWakeup) {
        close(onWakeup);
    }

    /** Force-close regardless of remaining subscribers (used by context shutdown). */
    @Override
    void close();
}
