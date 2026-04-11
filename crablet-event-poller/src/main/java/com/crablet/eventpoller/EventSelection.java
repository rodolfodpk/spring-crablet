package com.crablet.eventpoller;

import java.util.Map;
import java.util.Set;

/**
 * Shared event-matching contract for poller-backed modules.
 *
 * <p>Implementations describe which event types and tags should be delivered to a
 * processor instance. Empty collections mean "no restriction" for that dimension.
 */
public interface EventSelection {

    /**
     * Event types that should match. Empty set means all event types.
     */
    default Set<String> getEventTypes() {
        return Set.of();
    }

    /**
     * Tag keys that must all be present on the event.
     */
    default Set<String> getRequiredTags() {
        return Set.of();
    }

    /**
     * Tag keys where at least one must be present on the event.
     */
    default Set<String> getAnyOfTags() {
        return Set.of();
    }

    /**
     * Exact tag key/value pairs that must be present on the event.
     */
    default Map<String, String> getExactTags() {
        return Map.of();
    }
}
