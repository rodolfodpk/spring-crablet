package com.crablet.examples.courses.testutils;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.crablet.examples.courses.domain.event.CourseDefined;
import com.crablet.examples.courses.domain.event.CourseCapacityChanged;
import com.crablet.examples.courses.domain.event.StudentSubscribedToCourse;

import java.util.List;

/**
 * Test utilities for course domain tests.
 * Provides helper methods for creating test data and assertions.
 */
public class CourseTestUtils {

    // Static singleton ObjectMapper to avoid expensive creation on every call
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Create a StoredEvent from a CourseDefined event for testing.
     */
    public static StoredEvent createEvent(CourseDefined courseDefined) {
        try {
            byte[] data = OBJECT_MAPPER.writeValueAsBytes(courseDefined);
            List<Tag> tags = List.of(new Tag("course_id", courseDefined.courseId()));

            return new StoredEvent(
                    "CourseDefined",
                    tags,
                    data,
                    "1", // Mock transaction ID
                    1L, // Mock position
                    courseDefined.definedAt()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test event", e);
        }
    }

    /**
     * Create a StoredEvent from a CourseCapacityChanged event for testing.
     */
    public static StoredEvent createEvent(CourseCapacityChanged capacityChanged) {
        try {
            byte[] data = OBJECT_MAPPER.writeValueAsBytes(capacityChanged);
            List<Tag> tags = List.of(new Tag("course_id", capacityChanged.courseId()));

            return new StoredEvent(
                    "CourseCapacityChanged",
                    tags,
                    data,
                    "1", // Mock transaction ID
                    1L, // Mock position
                    capacityChanged.changedAt()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test event", e);
        }
    }

    /**
     * Create a StoredEvent from a StudentSubscribedToCourse event for testing.
     */
    public static StoredEvent createEvent(StudentSubscribedToCourse subscription) {
        try {
            byte[] data = OBJECT_MAPPER.writeValueAsBytes(subscription);
            // Multi-entity event: tags for both course and student
            List<Tag> tags = List.of(
                    new Tag("course_id", subscription.courseId()),
                    new Tag("student_id", subscription.studentId())
            );

            return new StoredEvent(
                    "StudentSubscribedToCourse",
                    tags,
                    data,
                    "1", // Mock transaction ID
                    1L, // Mock position
                    subscription.subscribedAt()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test event", e);
        }
    }

    /**
     * Deserialize event data to a specific type.
     */
    public static <T> T deserializeEventData(byte[] data, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }

    /**
     * Deserialize event data from Object to a specific type.
     * If object is already of type T, cast it directly. Otherwise serialize and deserialize.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeEventData(Object data, Class<T> clazz) {
        try {
            if (clazz.isInstance(data)) {
                return (T) data;
            }
            return OBJECT_MAPPER.convertValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }

    /**
     * Create a test EventDeserializer for deserializing events.
     */
    public static EventDeserializer createEventDeserializer() {
        return new EventDeserializer() {
            @Override
            public <E> E deserialize(StoredEvent event, Class<E> eventType) {
                try {
                    return OBJECT_MAPPER.readValue(event.data(), eventType);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize event", e);
                }
            }
        };
    }
}

