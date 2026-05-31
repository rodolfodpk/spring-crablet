package com.crablet.test;

import com.crablet.eventstore.EventType;
import com.fasterxml.jackson.annotation.JsonSubTypes;

import java.util.Arrays;
import java.util.Objects;

/**
 * Test helper for Crablet's event type naming contract.
 */
public final class EventTypeContract {

    private EventTypeContract() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Assert that {@link JsonSubTypes} names match {@link EventType#type(Class)} for the given hierarchy.
     */
    public static void assertJsonSubTypesMatchEventType(Class<?> jsonSubTypesRoot) {
        Objects.requireNonNull(jsonSubTypesRoot, "jsonSubTypesRoot must not be null");
        JsonSubTypes jsonSubTypes = jsonSubTypesRoot.getAnnotation(JsonSubTypes.class);
        if (jsonSubTypes == null) {
            throw new AssertionError(jsonSubTypesRoot.getName() + " is not annotated with @JsonSubTypes");
        }

        Arrays.stream(jsonSubTypes.value()).forEach(subType -> {
            Class<?> eventClass = subType.value();
            String derivedType = EventType.type(eventClass);
            if (!derivedType.equals(subType.name())) {
                throw new AssertionError(jsonSubTypesRoot.getName() +
                        " declares @JsonSubTypes name '" + subType.name() +
                        "' for " + eventClass.getName() +
                        ", but EventType.type(...) returns '" + derivedType + "'");
            }
        });
    }
}
