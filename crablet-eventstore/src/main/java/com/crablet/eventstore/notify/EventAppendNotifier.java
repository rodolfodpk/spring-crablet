package com.crablet.eventstore.notify;

import java.util.Set;

/**
 * Optional hook triggered after new events are committed to the event store.
 *
 * <p>Implementations must never compromise append correctness. Failures should
 * be treated as best-effort notification failures rather than append failures.
 */
public interface EventAppendNotifier {

    void notifyEventsAppended();

    /**
     * Notify with the set of event type names that were appended.
     *
     * <p>Implementations may encode the types in the notification payload so that
     * subscribers can skip polling when no subscribed event types were appended.
     * An empty set signals "unknown types" — implementations must treat it as a
     * wildcard and wake all subscribers.
     *
     * <p>The default implementation delegates to {@link #notifyEventsAppended()},
     * which sends a wildcard notification. Override in implementations that support
     * type-encoded payloads.
     */
    default void notifyEventsAppended(Set<String> eventTypes) {
        notifyEventsAppended();
    }
}
