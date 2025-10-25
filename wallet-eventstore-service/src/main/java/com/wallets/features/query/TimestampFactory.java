package com.wallets.features.query;

import java.time.Instant;

/**
 * Factory for parsing timestamp parameters with consistent defaults.
 */
public class TimestampFactory {

    /**
     * Parse timestamp parameter with fallback to null (no filtering).
     * This is the default behavior for both events and commands endpoints.
     *
     * @param timestamp The timestamp string to parse (can be null or empty)
     * @return Parsed Instant or null if timestamp is null/empty (no filtering)
     */
    public static Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null; // No timestamp filtering by default
        }
        return Instant.parse(timestamp);
    }

    /**
     * Parse timestamp parameter with fallback to null (no filtering).
     * Use this when you want to disable timestamp filtering by default.
     *
     * @param timestamp The timestamp string to parse (can be null or empty)
     * @return Parsed Instant or null if timestamp is null/empty
     */
    public static Instant parseTimestampOrNull(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        return Instant.parse(timestamp);
    }
}
