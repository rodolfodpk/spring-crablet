package com.crablet.eventpoller;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Union of {@link #getEventTypes()} across {@code selections}. If any selection is
     * unrestricted on this dimension (empty set), the union is unrestricted too — a
     * module-level filter must be broad enough to cover every selection it fans out to.
     */
    static Set<String> unionEventTypes(Collection<? extends EventSelection> selections) {
        if (selections.stream().anyMatch(s -> s.getEventTypes().isEmpty())) {
            return Set.of();
        }
        return selections.stream().flatMap(s -> s.getEventTypes().stream()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Union of {@link #getRequiredTags()} across {@code selections}. See {@link #unionEventTypes}
     * for the unrestricted-wins semantics.
     */
    static Set<String> unionRequiredTags(Collection<? extends EventSelection> selections) {
        if (selections.stream().anyMatch(s -> s.getRequiredTags().isEmpty())) {
            return Set.of();
        }
        return selections.stream().flatMap(s -> s.getRequiredTags().stream()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Union of {@link #getAnyOfTags()} across {@code selections}. See {@link #unionEventTypes}
     * for the unrestricted-wins semantics.
     */
    static Set<String> unionAnyOfTags(Collection<? extends EventSelection> selections) {
        if (selections.stream().anyMatch(s -> s.getAnyOfTags().isEmpty())) {
            return Set.of();
        }
        return selections.stream().flatMap(s -> s.getAnyOfTags().stream()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Union of {@link #getExactTags()} keys across {@code selections}. See {@link #unionEventTypes}
     * for the unrestricted-wins semantics.
     */
    static Set<String> unionExactTagKeys(Collection<? extends EventSelection> selections) {
        if (selections.stream().anyMatch(s -> s.getExactTags().isEmpty())) {
            return Set.of();
        }
        return selections.stream().flatMap(s -> s.getExactTags().keySet().stream()).collect(Collectors.toUnmodifiableSet());
    }
}
