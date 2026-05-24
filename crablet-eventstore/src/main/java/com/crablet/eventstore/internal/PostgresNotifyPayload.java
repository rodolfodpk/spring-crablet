package com.crablet.eventstore.internal;

import com.crablet.eventstore.Internal;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encodes append metadata into the compact LISTEN/NOTIFY payload understood by
 * the poller wakeup source.
 */
@Internal
public final class PostgresNotifyPayload {

    private PostgresNotifyPayload() {
    }

    /**
     * Encode types and tag key names into a {@code pg_notify} payload.
     *
     * <p>Format: {@code "Type1,Type2|key1,key2,key3"} — types before {@code |},
     * tag key names (not values) after. Falls back to types-only if the combined
     * string exceeds 7 900 bytes, then to {@code "*"} if even types are too long.
     */
    public static String encodePayload(Set<String> eventTypes, Set<String> tagKeys) {
        if (eventTypes.isEmpty()) return "*"; // unknown types -> wildcard

        String typesPart = eventTypes.stream().sorted().collect(Collectors.joining(","));
        if (tagKeys.isEmpty()) {
            return typesPart.length() <= 7900 ? typesPart : "*";
        }
        String tagPart = tagKeys.stream().sorted().collect(Collectors.joining(","));
        String combined = typesPart + "|" + tagPart;
        if (combined.length() <= 7900) return combined;
        // Tag keys made it too long: degrade to types-only, then wildcard.
        return typesPart.length() <= 7900 ? typesPart : "*";
    }
}
