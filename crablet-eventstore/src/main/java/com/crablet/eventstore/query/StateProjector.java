package com.crablet.eventstore.query;

import com.crablet.eventstore.store.StoredEvent;
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
     */
    String getId();

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
}
