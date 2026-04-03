package com.crablet.eventstore.query;

import com.crablet.eventstore.store.Cursor;
import org.jspecify.annotations.Nullable;

/**
 * ProjectionResult represents the result of a projection operation.
 * This is a pure data record with no business logic.
 */
public record ProjectionResult<T>(
        T states,
        @Nullable Cursor cursor
) {
    /**
     * Create a projection result from states and cursor.
     */
    public static <T> ProjectionResult<T> of(T states, @Nullable Cursor cursor) {
        return new ProjectionResult<>(states, cursor);
    }

    /**
     * Create a projection result from states only.
     */
    public static <T> ProjectionResult<T> of(T states) {
        return new ProjectionResult<>(states, null);
    }

    /**
     * Get the projected state.
     */
    public T state() {
        return states;
    }
}
