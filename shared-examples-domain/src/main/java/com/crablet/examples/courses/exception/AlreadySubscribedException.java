package com.crablet.examples.courses.domain.exception;

/**
 * Exception thrown when a student attempts to subscribe to a course they are already subscribed to.
 */
public class AlreadySubscribedException extends RuntimeException {

    public final String studentId;
    public final String courseId;

    public AlreadySubscribedException(String studentId, String courseId) {
        super(String.format("Student \"%s\" already subscribed to course \"%s\"", studentId, courseId));
        this.studentId = studentId;
        this.courseId = courseId;
    }
}

