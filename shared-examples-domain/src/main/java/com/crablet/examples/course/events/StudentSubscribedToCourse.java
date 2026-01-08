package com.crablet.examples.course.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * StudentSubscribedToCourse represents when a student subscribes to a course.
 * This is a multi-entity event that affects both course and student entities.
 * This is the event data structure.
 */
public record StudentSubscribedToCourse(
        @JsonProperty("student_id") String studentId,
        @JsonProperty("course_id") String courseId,
        @JsonProperty("subscribed_at") Instant subscribedAt
) implements CourseEvent {

    public StudentSubscribedToCourse {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty");
        }
        if (courseId == null || courseId.trim().isEmpty()) {
            throw new IllegalArgumentException("Course ID cannot be null or empty");
        }
        if (subscribedAt == null) {
            throw new IllegalArgumentException("Subscribed at timestamp cannot be null");
        }
    }

    /**
     * Create a StudentSubscribedToCourse event.
     */
    public static StudentSubscribedToCourse of(String studentId, String courseId) {
        return new StudentSubscribedToCourse(studentId, courseId, Instant.now());
    }
}

