package com.crablet.eventstore.notify;

/**
 * Optional hook triggered after new events are committed to the event store.
 *
 * <p>Implementations must never compromise append correctness. Failures should
 * be treated as best-effort notification failures rather than append failures.
 */
public interface EventAppendNotifier {

    void notifyEventsAppended();
}
