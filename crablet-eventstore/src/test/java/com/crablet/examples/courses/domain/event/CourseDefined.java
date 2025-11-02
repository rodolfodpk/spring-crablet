package com.crablet.examples.courses.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * CourseDefined represents when a course is registered.
 * This is the event data structure.
 */
public record CourseDefined(
        @JsonProperty("course_id") String courseId,
        @JsonProperty("capacity") int capacity,
        @JsonProperty("defined_at") Instant definedAt
) implements CourseEvent {

    public CourseDefined {
        if (courseId == null || courseId.trim().isEmpty()) {
            throw new IllegalArgumentException("Course ID cannot be null or empty");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (definedAt == null) {
            throw new IllegalArgumentException("Defined at timestamp cannot be null");
        }
    }

    /**
     * Create a CourseDefined event.
     */
    public static CourseDefined of(String courseId, int capacity) {
        return new CourseDefined(courseId, capacity, Instant.now());
    }
}

