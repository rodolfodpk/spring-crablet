package com.crablet.eventstore;

/**
 * EventDeserializer provides utilities for deserializing events during projection.
 * Passed to StateProjector.transition() to enable optional event deserialization.
 * <p>
 * This functional interface is intentionally kept separate from BiFunction to:
 * - Provide clear semantics for event deserialization
 * - Enable future extension (e.g., caching, validation)
 * - Improve readability over generic function types
 */
@FunctionalInterface
public interface EventDeserializer {
    /**
     * Deserialize a StoredEvent to a typed domain event.
     * 
     * @param event The stored event with raw bytes
     * @param eventType The target event class (e.g., WalletEvent.class)
     * @return Deserialized event instance
     * @throws RuntimeException if deserialization fails
     */
    <E> E deserialize(StoredEvent event, Class<E> eventType);
}

