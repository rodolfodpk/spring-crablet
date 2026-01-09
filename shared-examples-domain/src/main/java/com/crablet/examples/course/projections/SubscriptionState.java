package com.crablet.examples.course.projections;

/**
 * State for subscription operations - aggregates all projections needed for subscription validation.
 * <p>
 * This record contains only what SubscribeStudentToCourseCommandHandler needs to make decisions
 * about subscription operations, following the DCB principle of projecting minimal state.
 */
public record SubscriptionState(
        boolean courseExists,
        int courseCapacity,
        int courseSubscriptionsCount,
        int studentSubscriptionsCount,
        boolean studentAlreadySubscribed
) {

    /**
     * Check if course is at capacity.
     */
    public boolean isCourseFull() {
        return courseSubscriptionsCount >= courseCapacity;
    }

    /**
     * Check if student has reached subscription limit.
     */
    public boolean hasReachedSubscriptionLimit(int maxSubscriptions) {
        return studentSubscriptionsCount >= maxSubscriptions;
    }
}

