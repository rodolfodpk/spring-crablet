package com.crablet.eventstore.query;

import com.crablet.eventstore.StoredEvent;

import java.util.List;

/**
 * StateProjector represents a projector for state reconstruction from events.
 * This is a functional interface for state projection logic.
 * <p>
 * Per DCB specification: filtering by tags happens in the Query (decision model),
 * not in the projector. The projector receives pre-filtered events from the query.
 */
public interface StateProjector<T> {

    /**
     * Get the unique identifier for this projector.
     * Defaults to the simple class name. Override when multiple instances of the same class
     * project different entities (e.g., include an entity ID in the returned string).
     */
    default String getId() {
        return getClass().getSimpleName();
    }

    /**
     * Get the event types this projector handles.
     * Events from the query that don't match these types will be skipped.
     */
    List<String> getEventTypes();

    /**
     * Get the initial state for this projector.
     */
    T getInitialState();

    /**
     * Transition the state based on an event.
     * <p>
     * Called for each event from the query that matches getEventTypes().
     * Events are already filtered by tags in the Query (decision model).
     *
     * @param currentState The current projected state
     * @param event The stored event to process
     * @param deserializer The event deserializer for converting raw events to typed events
     * @return The new state after applying the event
     */
    T transition(T currentState, StoredEvent event, EventDeserializer deserializer);

    /**
     * Returns a projector that yields {@code true} if any matching event exists.
     * <p>
     * For simple existence checks prefer {@code EventStore.exists(query)} which is a one-liner.
     * Use this factory directly only when composing with other projectors inside a multi-projector
     * {@code project()} call.
     * <p>
     * When called with no arguments, {@code getEventTypes()} returns an empty list which the
     * framework treats as "accept all types" — the {@code Query} already scopes the event types
     * at the database level.
     *
     * @param eventTypes optional in-memory type filter (empty = accept all types the query returns)
     * @return A {@code StateProjector<Boolean>} that returns {@code true} on the first matching event
     */
    static StateProjector<Boolean> exists(String... eventTypes) {
        return new StateProjector<>() {
            @Override
            public List<String> getEventTypes() { return List.of(eventTypes); }

            @Override
            public Boolean getInitialState() { return false; }

            @Override
            public Boolean transition(Boolean state, StoredEvent event, EventDeserializer deserializer) {
                return true;
            }
        };
    }
}
