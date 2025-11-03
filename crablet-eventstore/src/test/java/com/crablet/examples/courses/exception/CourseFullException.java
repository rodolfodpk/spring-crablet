package com.crablet.examples.courses.domain.exception;

/**
 * Exception thrown when attempting to subscribe a student to a course that is already at capacity.
 */
public class CourseFullException extends RuntimeException {

    public final String courseId;
    public final int currentSubscriptions;
    public final int capacity;

    public CourseFullException(String courseId, int currentSubscriptions, int capacity) {
        super(String.format("Course \"%s\" is already fully booked (subscribed: %d, capacity: %d)", 
                courseId, currentSubscriptions, capacity));
        this.courseId = courseId;
        this.currentSubscriptions = currentSubscriptions;
        this.capacity = capacity;
    }
}

