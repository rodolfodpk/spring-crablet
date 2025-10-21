package com.crablet.core;

import java.util.Objects;

/**
 * AppendCondition defines the conditions for appending events.
 * Supports dual conditions: concurrency check (with cursor) and idempotency check (no cursor).
 * This follows the DCB specification where idempotency checks ignore cursor position.
 */
public record AppendCondition(
        Cursor afterCursor,
        Query stateChanged,      // Concurrency check (with cursor)
        Query alreadyExists      // Idempotency check (no cursor)
) {

    public static AppendCondition of(Cursor afterCursor, Query stateChanged) {
        if (afterCursor == null) {
            throw new IllegalArgumentException("afterCursor cannot be null");
        }
        if (stateChanged == null) {
            throw new IllegalArgumentException("stateChanged cannot be null");
        }
        return new AppendCondition(afterCursor, stateChanged, null);
    }

    public static AppendCondition of(Cursor afterCursor, Query stateChangedQuery, Query alreadyExistsQuery) {
        if (afterCursor == null) {
            throw new IllegalArgumentException("afterCursor cannot be null");
        }
        if (stateChangedQuery == null) {
            throw new IllegalArgumentException("stateChangedQuery cannot be null");
        }
        return new AppendCondition(afterCursor, stateChangedQuery, alreadyExistsQuery);
    }

    public static AppendCondition of(Cursor afterCursor) {
        return of(afterCursor, Query.empty());
    }

    /**
     * Create condition for new streams WITHOUT idempotency protection.
     * Use when creating the first event in a stream and duplicates are not a concern.
     */
    public static AppendCondition expectEmptyStream() {
        return new AppendCondition(Cursor.zero(), Query.empty(), null);
    }

    /**
     * Create condition for new streams WITH idempotency protection.
     * Use when creating the first event in a stream and you want to prevent duplicates.
     */
    public static AppendCondition expectEmptyStreamWith(Query idempotencyCheck) {
        if (idempotencyCheck == null) {
            throw new IllegalArgumentException("idempotencyCheck cannot be null");
        }
        return new AppendCondition(Cursor.zero(), Query.empty(), idempotencyCheck);
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
                Objects.equals(stateChanged, that.stateChanged) &&
                Objects.equals(alreadyExists, that.alreadyExists);
    }

    @Override
    public int hashCode() {
        return Objects.hash(afterCursor, stateChanged, alreadyExists);
    }

    @Override
    public String toString() {
        return "AppendCondition{" +
                "afterCursor=" + afterCursor +
                ", stateChanged=" + stateChanged +
                ", alreadyExists=" + alreadyExists +
                '}';
    }
}
