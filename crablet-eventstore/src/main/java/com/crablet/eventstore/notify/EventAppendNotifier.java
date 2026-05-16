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
     * Notify with the event types and tag key names that were appended.
     *
     * <p>Implementations encode this in the {@code pg_notify} payload so that
     * subscribers can skip polling when neither their event types nor their tag
     * key criteria intersect with what was appended.
     *
     * <p>Only tag <em>key names</em> (not values) are sent — high-cardinality values
     * are excluded to keep the payload compact. Exact-tag value matching is left to
     * the SQL filter layer. Empty sets signal "unknown" and implementations must treat
     * them as wildcards.
     *
     * @param eventTypes type names of the appended events; empty = wildcard
     * @param tagKeys    tag key names from all tags on the appended events; empty = no tag info
     */
    default void notifyEventsAppended(Set<String> eventTypes, Set<String> tagKeys) {
        notifyEventsAppended();
    }

    /**
     * Convenience overload for callers that only have event types (no tag info).
     * Delegates to {@link #notifyEventsAppended(Set, Set)} with an empty tag key set.
     */
    default void notifyEventsAppended(Set<String> eventTypes) {
        notifyEventsAppended(eventTypes, Set.of());
    }
}
