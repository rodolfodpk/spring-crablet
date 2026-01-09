package com.crablet.examples.course.exceptions;

/**
 * Exception thrown when attempting to perform operations on a course that does not exist.
 */
public class CourseNotFoundException extends RuntimeException {

    public final String courseId;

    public CourseNotFoundException(String courseId) {
        super("Course not found: " + courseId);
        this.courseId = courseId;
    }
}

