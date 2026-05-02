package com.crablet.examples.course.exceptions;

/**
 * Exception thrown when attempting to subscribe a student that is not registered.
 */
public class StudentNotFoundException extends RuntimeException {

    public final String studentId;

    public StudentNotFoundException(String studentId) {
        super("Student not found: " + studentId);
        this.studentId = studentId;
    }
}
