package com.crablet.eventstore.integration;

import com.crablet.eventstore.store.StoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Helper utilities for DCB compliance testing.
 * Provides factory methods for creating test events and extracting cursors.
 */
public class DCBTestHelpers {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()); // Support Java 8 date/time

    /**
     * Deserializes a stored event's data to a specific type.
     */
    public static <T> T deserialize(StoredEvent event, Class<T> clazz) {
        try {
            return objectMapper.readValue(event.data(), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}

