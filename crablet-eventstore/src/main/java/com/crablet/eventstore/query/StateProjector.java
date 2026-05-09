package com.crablet.eventstore.query;

import com.crablet.eventstore.EventType;
import com.crablet.eventstore.Stable;
import com.crablet.eventstore.StoredEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StateProjector represents a projector for state reconstruction from events.
 * This is a functional interface for state projection logic.
 * <p>
 * Per DCB specification: filtering by tags happens in the Query (decision model),
 * not in the projector. The projector receives pre-filtered events from the query.
 * <p>
 * For new projectors, prefer the fluent {@link #builder(String, Object)} factory over
 * hand-writing {@code getEventTypes()} and a string-based {@code switch} in
 * {@code transition()}. Existing hand-written implementations remain fully supported.
 */
@Stable
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

    /**
     * Start building a projector with typed per-event handlers.
     * <p>
     * Usage:
     * <pre>{@code
     * StateProjector<MyState> projector =
     *     StateProjector.<MyState>builder("my-projector-id", MyState.initial())
     *         .on(SomeEvent.class, (state, event) -> state.withSomething(event.value()))
     *         .on(OtherEvent.class, (state, event) -> state.withOther(event.count()))
     *         .build();
     * }</pre>
     *
     * @param id           unique projector id (returned by {@link #getId()})
     * @param initialState initial state (returned by {@link #getInitialState()})
     */
    static <T> Builder<T> builder(String id, T initialState) {
        return new Builder<>(id, initialState);
    }

    /**
     * Typed handler for a single event type used by {@link Builder}.
     */
    @FunctionalInterface
    interface EventTransition<T, E> {
        T apply(T state, E event);
    }

    /**
     * Fluent builder for {@link StateProjector}.
     * Register one handler per event class with {@link #on}; call {@link #build} once.
     * Calling {@link #on} after {@link #build} throws {@link IllegalStateException}.
     */
    final class Builder<T> {

        private final String id;
        private final T initialState;
        private final LinkedHashMap<String, Class<?>> eventClasses = new LinkedHashMap<>();
        private final LinkedHashMap<String, EventTransition<T, ?>> transitions = new LinkedHashMap<>();
        private boolean built = false;

        private Builder(String id, T initialState) {
            this.id = id;
            this.initialState = initialState;
        }

        /**
         * Register a typed handler for {@code eventClass}.
         * Uses {@link EventType#type(Class)} to derive the event type string.
         *
         * @throws IllegalStateException     if called after {@link #build()}
         * @throws IllegalArgumentException  if the same event class is registered twice
         */
        public <E> Builder<T> on(Class<E> eventClass, EventTransition<T, E> transition) {
            if (built) {
                throw new IllegalStateException(
                        "Builder has already been built; create a new builder to register more handlers");
            }
            String type = EventType.type(eventClass);
            if (eventClasses.containsKey(type)) {
                throw new IllegalArgumentException("Duplicate event type registration: " + type);
            }
            eventClasses.put(type, eventClass);
            transitions.put(type, transition);
            return this;
        }

        /**
         * Build the {@link StateProjector}.
         * Snapshots the registered handlers; the builder must not be used after this call.
         */
        public StateProjector<T> build() {
            built = true;
            List<String> orderedTypes = List.copyOf(eventClasses.keySet());
            Map<String, Class<?>> classSnapshot = Map.copyOf(eventClasses);
            Map<String, EventTransition<T, ?>> transitionSnapshot = Map.copyOf(transitions);
            String projectorId = id;
            T projectorInitial = initialState;

            return new StateProjector<>() {
                @Override
                public String getId() { return projectorId; }

                @Override
                public List<String> getEventTypes() { return orderedTypes; }

                @Override
                public T getInitialState() { return projectorInitial; }

                @Override
                @SuppressWarnings("unchecked")
                public T transition(T currentState, StoredEvent event, EventDeserializer deserializer) {
                    Class<?> eventClass = classSnapshot.get(event.type());
                    EventTransition<T, ?> transition = transitionSnapshot.get(event.type());
                    if (eventClass == null || transition == null) return currentState;
                    // Safe: on() pairs eventClass and transition atomically for the same type,
                    // so deserialize(event, eventClass) returns the E that transition expects.
                    Object deserialized = deserializer.deserialize(event, eventClass);
                    return ((EventTransition<T, Object>) transition).apply(currentState, deserialized);
                }
            };
        }
    }
}
