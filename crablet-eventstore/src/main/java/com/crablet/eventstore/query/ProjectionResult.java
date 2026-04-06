package com.crablet.eventstore.query;

import com.crablet.eventstore.StreamPosition;
import org.jspecify.annotations.Nullable;

/**
 * ProjectionResult represents the result of a projection operation.
 * This is a pure data record with no business logic.
 */
public record ProjectionResult<T>(
        T state,
        @Nullable StreamPosition streamPosition
) {
    /**
     * Create a projection result from state and stream position.
     */
    public static <T> ProjectionResult<T> of(T state, @Nullable StreamPosition streamPosition) {
        return new ProjectionResult<>(state, streamPosition);
    }

    /**
     * Create a projection result from state only.
     */
    public static <T> ProjectionResult<T> of(T state) {
        return new ProjectionResult<>(state, null);
    }
}
