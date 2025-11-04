package com.crablet.examples.courses.domain;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;

import static com.crablet.examples.courses.domain.CourseEventTypes.*;
import static com.crablet.examples.courses.domain.CourseTags.*;

/**
 * Reusable query patterns for course operations.
 * Encapsulates DCB decision model queries for course domain.
 */
public class CourseQueryPatterns {

    /**
     * Complete decision model query for subscription operations.
     * Includes all events affecting both course AND student entities.
     * <p>
     * This query enables multi-entity constraint enforcement:
     * - Course-side: checks course existence, capacity, and subscription count
     * - Student-side: checks student subscription count and duplicate subscriptions
     */
    public static Query subscriptionDecisionModel(String courseId, String studentId) {
        return QueryBuilder.create()
                // Course-side: course definition and capacity changes
                .events(COURSE_DEFINED, COURSE_CAPACITY_CHANGED)
                .tag(COURSE_ID, courseId)
                // Course-side: subscriptions to this course (for capacity check)
                .event(STUDENT_SUBSCRIBED_TO_COURSE, COURSE_ID, courseId)
                // Student-side: subscriptions by this student (for limit check)
                .event(STUDENT_SUBSCRIBED_TO_COURSE, STUDENT_ID, studentId)
                .build();
    }

    /**
     * Decision model query for course operations (define, change capacity).
     * Includes all events affecting a single course.
     */
    public static Query courseDecisionModel(String courseId) {
        return QueryBuilder.create()
                .events(COURSE_DEFINED, COURSE_CAPACITY_CHANGED)
                .tag(COURSE_ID, courseId)
                .build();
    }
}

