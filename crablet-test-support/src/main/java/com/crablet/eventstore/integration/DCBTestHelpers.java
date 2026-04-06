package com.crablet.eventstore.integration;

import com.crablet.eventstore.StoredEvent;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;


/**
 * Helper utilities for DCB compliance testing.
 * Provides factory methods for creating test events and extracting stream positions.
 */
public class DCBTestHelpers {

    private static final ObjectMapper objectMapper = JsonMapper.builder().build()
            ; // Support Java 8 date/time

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

