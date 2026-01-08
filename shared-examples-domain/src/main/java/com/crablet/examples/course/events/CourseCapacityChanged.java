package com.crablet.examples.course.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * CourseCapacityChanged represents when a course capacity is updated.
 * This is the event data structure.
 */
public record CourseCapacityChanged(
        @JsonProperty("course_id") String courseId,
        @JsonProperty("new_capacity") int newCapacity,
        @JsonProperty("changed_at") Instant changedAt
) implements CourseEvent {

    public CourseCapacityChanged {
        if (courseId == null || courseId.trim().isEmpty()) {
            throw new IllegalArgumentException("Course ID cannot be null or empty");
        }
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("New capacity must be positive");
        }
        if (changedAt == null) {
            throw new IllegalArgumentException("Changed at timestamp cannot be null");
        }
    }

    /**
     * Create a CourseCapacityChanged event.
     */
    public static CourseCapacityChanged of(String courseId, int newCapacity) {
        return new CourseCapacityChanged(courseId, newCapacity, Instant.now());
    }
}

