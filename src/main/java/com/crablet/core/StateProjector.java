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
     */
    T transition(T currentState, StoredEvent event);

    /**
     * Check if this projector handles the given event.
     */
    default boolean handles(StoredEvent event) {
        // Check event types
        if (!getEventTypes().isEmpty() && !getEventTypes().contains(event.type())) {
            return false;
        }

        // Check tags
        if (!getTags().isEmpty() && !event.hasAnyTag(getTags())) {
            return false;
        }

        return true;
    }
}
