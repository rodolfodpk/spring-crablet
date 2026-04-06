package com.crablet.eventstore.query;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StreamPosition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Result of a projection operation, carrying both the projected state and the
 * stream position of the last event read.
 *
 * <p>For non-commutative operations (balance checks, capacity limits), prefer
 * {@link #appendNonCommutative(EventStore, List, Query)} over calling
 * {@code eventStore.appendNonCommutative(...)} directly — the stream position
 * is embedded in this result and therefore cannot be accidentally discarded.
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

    /**
     * Append events with a stream-position-based DCB conflict check, using the
     * stream position captured by this projection.
     *
     * <p>The stream position is implicit — it cannot be accidentally omitted or
     * replaced with a stale value. Use this method instead of calling
     * {@code eventStore.appendNonCommutative(...)} directly when working outside
     * a {@link com.crablet.command.NonCommutativeCommandHandler}.
     *
     * @param eventStore    the event store to append to
     * @param events        the events to append
     * @param decisionModel the query that scopes the conflict check (same query used for this projection)
     * @return the transaction ID
     * @throws ConcurrencyException  if a concurrent modification is detected after the captured stream position
     * @throws IllegalStateException if this result carries no stream position (no events matched the query)
     */
    public String appendNonCommutative(EventStore eventStore, List<AppendEvent> events, Query decisionModel) {
        if (streamPosition == null) {
            throw new IllegalStateException(
                    "Cannot call appendNonCommutative: projection returned no stream position " +
                    "(no events matched the query). Use appendIdempotent for entity creation.");
        }
        return eventStore.appendNonCommutative(events, decisionModel, streamPosition);
    }
}
