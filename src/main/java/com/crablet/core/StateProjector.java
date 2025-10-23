package com.crablet.core;

import java.util.List;

/**
 * StateProjector represents a projector for state reconstruction from events.
 * This is a functional interface for state projection logic.
 */
public interface StateProjector<T> {

    /**
     * Get the unique identifier for this projector.
     */
    String getId();

    /**
     * Get the event types this projector handles.
     */
    List<String> getEventTypes();

    /**
     * Get the tags this projector filters by.
     */
    List<Tag> getTags();

    /**
     * Get the initial state for this projector.
     */
    T getInitialState();

    /**
     * Transition the state based on an event.
     * 
     * @param currentState The current projected state
     * @param event The stored event to process
     * @param deserializer The event deserializer for converting raw events to typed events
     * @return The new state after applying the event
     */
    T transition(T currentState, StoredEvent event, EventDeserializer deserializer);

    /**
     * Check if this projector handles the given event.
     * 
     * @param event The stored event to check
     * @param deserializer The event deserializer (may be used for deserialization if needed)
     * @return true if this projector should process the event
     */
    default boolean handles(StoredEvent event, EventDeserializer deserializer) {
        // Check event types
        if (!getEventTypes().isEmpty() && !getEventTypes().contains(event.type())) {
            return false;
        }

        // Check tags
        return getTags().isEmpty() || event.hasAnyTag(getTags());
    }
}
