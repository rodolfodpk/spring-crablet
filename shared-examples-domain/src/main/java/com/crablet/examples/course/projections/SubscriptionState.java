package com.crablet.examples.course.projections;

/**
 * State for subscription operations - aggregates all projections needed for subscription validation.
 * <p>
 * This record contains only what SubscribeStudentToCourseCommandHandler needs to make decisions
 * about subscription operations, following the DCB principle of projecting minimal state.
 */
public record SubscriptionState(
        boolean courseExists,
        boolean studentExists,
        int courseCapacity,
        int courseSubscriptionsCount,
        int studentSubscriptionsCount,
        boolean studentAlreadySubscribed
) {

    public static SubscriptionState initial() {
        return new SubscriptionState(false, false, 0, 0, 0, false);
    }

    public SubscriptionState withCourse(int capacity) {
        return new SubscriptionState(true, studentExists, capacity,
                courseSubscriptionsCount, studentSubscriptionsCount, studentAlreadySubscribed);
    }

    public SubscriptionState withCapacity(int capacity) {
        return new SubscriptionState(courseExists, studentExists, capacity,
                courseSubscriptionsCount, studentSubscriptionsCount, studentAlreadySubscribed);
    }

    public SubscriptionState withStudentExists() {
        return new SubscriptionState(courseExists, true, courseCapacity,
                courseSubscriptionsCount, studentSubscriptionsCount, studentAlreadySubscribed);
    }

    public SubscriptionState applySubscription(boolean affectsCourse, boolean affectsStudent) {
        return new SubscriptionState(
                courseExists,
                studentExists,
                courseCapacity,
                affectsCourse ? courseSubscriptionsCount + 1 : courseSubscriptionsCount,
                affectsStudent ? studentSubscriptionsCount + 1 : studentSubscriptionsCount,
                studentAlreadySubscribed || (affectsCourse && affectsStudent));
    }

    public boolean isCourseFull() {
        return courseSubscriptionsCount >= courseCapacity;
    }

    public boolean hasReachedSubscriptionLimit(int maxSubscriptions) {
        return studentSubscriptionsCount >= maxSubscriptions;
    }
}
