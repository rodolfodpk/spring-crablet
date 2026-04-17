package com.crablet.eventstore.query;

import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StreamPosition;
import org.jspecify.annotations.Nullable;

/**
 * Result of a projection operation, carrying both the projected state and the
 * stream position of the last event read.
 *
 * <p>For non-commutative operations in the command framework, prefer returning
 * {@code CommandDecision.NonCommutative} from the handler.
 * For advanced direct-{@link EventStore} usage outside the command framework,
 * pass {@link #streamPosition()} explicitly to
 * {@link EventStore#appendNonCommutative(java.util.List, Query, StreamPosition)}.
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
