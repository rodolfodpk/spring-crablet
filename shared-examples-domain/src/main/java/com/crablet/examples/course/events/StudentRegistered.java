package com.crablet.examples.course.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * StudentRegistered represents when a student becomes eligible for course subscription.
 */
public record StudentRegistered(
        @JsonProperty("student_id") String studentId,
        @JsonProperty("registered_at") Instant registeredAt
) implements CourseEvent {

    public StudentRegistered {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty");
        }
        if (registeredAt == null) {
            throw new IllegalArgumentException("Registered at timestamp cannot be null");
        }
    }

    /**
     * Create a StudentRegistered event.
     */
    public static StudentRegistered of(String studentId) {
        return new StudentRegistered(studentId, Instant.now());
    }
}
