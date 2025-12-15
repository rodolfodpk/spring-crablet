package com.crablet.command.handlers.courses;

import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crablet.examples.courses.domain.event.CourseDefined;
import com.crablet.examples.courses.domain.event.CourseCapacityChanged;
import com.crablet.examples.courses.domain.event.StudentSubscribedToCourse;

import java.util.List;

/**
 * Test utilities for course command handler tests.
 * Provides helper methods for creating test data and assertions.
 * Note: This is NOT a Spring component - it's a utility class for tests.
 */
public class CourseTestUtils {

    private final ObjectMapper objectMapper;

    public CourseTestUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Create a StoredEvent from a CourseDefined event for testing.
     */
    public StoredEvent createEvent(CourseDefined courseDefined) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(courseDefined);
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
    public StoredEvent createEvent(CourseCapacityChanged capacityChanged) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(capacityChanged);
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
    public StoredEvent createEvent(StudentSubscribedToCourse subscription) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(subscription);
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
    public <T> T deserializeEventData(byte[] data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }

    /**
     * Deserialize event data from Object to a specific type.
     * If object is already of type T, cast it directly. Otherwise serialize and deserialize.
     */
    @SuppressWarnings("unchecked")
    public <T> T deserializeEventData(Object data, Class<T> clazz) {
        try {
            if (clazz.isInstance(data)) {
                return (T) data;
            }
            return objectMapper.convertValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }
}

