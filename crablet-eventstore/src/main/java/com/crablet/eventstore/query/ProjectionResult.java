package com.crablet.eventstore.query;

import com.crablet.eventstore.store.StreamPosition;
import org.jspecify.annotations.Nullable;

/**
 * ProjectionResult represents the result of a projection operation.
 * This is a pure data record with no business logic.
 */
public record ProjectionResult<T>(
        T states,
        @Nullable StreamPosition streamPosition
) {
    /**
     * Create a projection result from states and stream position.
     */
    public static <T> ProjectionResult<T> of(T states, @Nullable StreamPosition streamPosition) {
        return new ProjectionResult<>(states, streamPosition);
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
