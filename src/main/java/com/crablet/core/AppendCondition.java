package com.crablet.core;

import java.util.Objects;

/**
 * AppendCondition defines the conditions for appending events.
 * This is equivalent to the Go AppendCondition struct.
 */
public record AppendCondition(
        Cursor afterCursor,
        Query failIfEventsMatch
) {

    public static AppendCondition of(Cursor afterCursor, Query failIfEventsMatch) {
        if (afterCursor == null) {
            throw new IllegalArgumentException("afterCursor cannot be null");
        }
        if (failIfEventsMatch == null) {
            throw new IllegalArgumentException("failIfEventsMatch cannot be null");
        }
        return new AppendCondition(afterCursor, failIfEventsMatch);
    }

    public static AppendCondition of(Cursor afterCursor) {
        return of(afterCursor, Query.empty());
    }

    public static AppendCondition forEmptyStream() {
        return new AppendCondition(Cursor.zero(), Query.empty());
    }

    /**
     * Check if this condition expects an empty stream (position 0).
     */
    public boolean expectsEmptyStream() {
        return afterCursor.position().isZero();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppendCondition that = (AppendCondition) o;
        return Objects.equals(afterCursor, that.afterCursor) &&
                Objects.equals(failIfEventsMatch, that.failIfEventsMatch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(afterCursor, failIfEventsMatch);
    }

    @Override
    public String toString() {
        return "AppendCondition{" +
                "afterCursor=" + afterCursor +
                ", failIfEventsMatch=" + failIfEventsMatch +
                '}';
    }
}
