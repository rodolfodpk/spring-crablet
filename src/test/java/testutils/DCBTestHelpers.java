package testutils;

import com.crablet.core.AppendEvent;
import com.crablet.core.Cursor;
import com.crablet.core.EventStore;
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
        return AppendEvent.of(
            type,
            List.of(new Tag("test_id", id)),
            String.format("{\"id\": \"%s\"}", id).getBytes()
        );
    }
    
    /**
     * Creates a test event with custom tags.
     */
    public static AppendEvent createTestEvent(String type, Tag... tags) {
        return AppendEvent.of(type, List.of(tags), "{}".getBytes());
    }
    
    /**
     * Creates a test event with custom data.
     */
    public static AppendEvent createTestEvent(String type, byte[] data) {
        return AppendEvent.of(type, List.of(), data);
    }
    
    /**
     * Gets the cursor after the last event in the store.
     */
    public static Cursor getCursorAfterLastEvent(EventStore store) {
        List<StoredEvent> events = store.query(com.crablet.core.Query.empty(), null);
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

