package com.crablet.examples.courses.domain;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.examples.courses.domain.event.*;

import static com.crablet.eventstore.store.EventType.type;
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
                .events(type(CourseDefined.class), type(CourseCapacityChanged.class))
                .tag(COURSE_ID, courseId)
                // Course-side: subscriptions to this course (for capacity check)
                .event(type(StudentSubscribedToCourse.class), COURSE_ID, courseId)
                // Student-side: subscriptions by this student (for limit check)
                .event(type(StudentSubscribedToCourse.class), STUDENT_ID, studentId)
                .build();
    }

    /**
     * Decision model query for course operations (define, change capacity).
     * Includes all events affecting a single course.
     */
    public static Query courseDecisionModel(String courseId) {
        return QueryBuilder.create()
                .events(type(CourseDefined.class), type(CourseCapacityChanged.class))
                .tag(COURSE_ID, courseId)
                .build();
    }
}

