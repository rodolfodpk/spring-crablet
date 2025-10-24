package crablet.testutils;

import com.crablet.core.AppendEvent;
import com.crablet.core.Cursor;
import com.crablet.core.EventTestHelper;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

/**
 * Helper utilities for DCB compliance testing.
 * Provides factory methods for creating test events and extracting cursors.
 */
public class DCBTestHelpers {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()); // Support Java 8 date/time

    /**
     * Creates a test event with a single test_id tag.
     */
    public static AppendEvent createTestEvent(String type, String id) {
        return AppendEvent.builder(type)
                .tag("test_id", id)
                .data(String.format("{\"id\": \"%s\"}", id))
                .build();
    }

    /**
     * Creates a test event with custom tags.
     */
    public static AppendEvent createTestEvent(String type, Tag... tags) {
        AppendEvent.Builder builder = AppendEvent.builder(type);
        for (Tag tag : tags) {
            builder.tag(tag.key(), tag.value());
        }
        return builder.data("{}").build();
    }

    /**
     * Creates a test event with custom data.
     */
    public static AppendEvent createTestEvent(String type, byte[] data) {
        return AppendEvent.builder(type)
                .data(data)
                .build();
    }

    /**
     * Gets the cursor after the last event in the store.
     */
    public static Cursor getCursorAfterLastEvent(EventTestHelper testHelper) {
        List<StoredEvent> events = testHelper.query(com.crablet.core.Query.empty(), null);
        if (events.isEmpty()) {
            return Cursor.zero();
        }
        StoredEvent last = events.get(events.size() - 1);
        return Cursor.of(last.position(), last.occurredAt(), last.transactionId());
    }

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

