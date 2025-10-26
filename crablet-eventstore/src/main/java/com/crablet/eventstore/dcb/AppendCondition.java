package com.crablet.eventstore.dcb;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.Cursor;

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
}
