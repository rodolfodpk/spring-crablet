package com.crablet.eventpoller;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;

import java.util.List;
import java.util.Set;

/**
 * In-memory counterpart of {@link EventSelectionSqlBuilder}.
 * Mirrors the SQL WHERE logic exactly so fan-out routing produces the same results
 * as the database-side filter used in the legacy per-processor path.
 */
public final class EventSelectionMatcher {

    private EventSelectionMatcher() {}

    public static boolean matches(EventSelection selection, StoredEvent event) {
        Set<String> eventTypes = selection.getEventTypes();
        if (!eventTypes.isEmpty() && !eventTypes.contains(event.type())) {
            return false;
        }

        List<Tag> tags = event.tags();

        for (String requiredKey : selection.getRequiredTags()) {
            if (tags.stream().noneMatch(t -> requiredKey.equals(t.key()))) {
                return false;
            }
        }

        Set<String> anyOfTags = selection.getAnyOfTags();
        if (!anyOfTags.isEmpty() && tags.stream().noneMatch(t -> anyOfTags.contains(t.key()))) {
            return false;
        }

        for (var entry : selection.getExactTags().entrySet()) {
            if (tags.stream().noneMatch(t -> entry.getKey().equals(t.key()) && entry.getValue().equals(t.value()))) {
                return false;
            }
        }

        return true;
    }
}
