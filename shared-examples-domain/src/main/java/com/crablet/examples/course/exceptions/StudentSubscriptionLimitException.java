package com.crablet.examples.course.exceptions;

/**
 * Exception thrown when a student attempts to subscribe to more than the maximum allowed courses.
 */
public class StudentSubscriptionLimitException extends RuntimeException {

    public final String studentId;
    public final int currentSubscriptions;
    public final int maxSubscriptions;

    public StudentSubscriptionLimitException(String studentId, int currentSubscriptions, int maxSubscriptions) {
        super(String.format("Student \"%s\" already subscribed to %d courses (max: %d)", 
                studentId, currentSubscriptions, maxSubscriptions));
        this.studentId = studentId;
        this.currentSubscriptions = currentSubscriptions;
        this.maxSubscriptions = maxSubscriptions;
    }
}

