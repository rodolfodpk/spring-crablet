package com.crablet.examples.courses.domain;

/**
 * Constants for course domain event type names.
 * 
 * This class provides centralized event type constants to:
 * - Prevent typos in event type names
 * - Make event types easily discoverable
 * - Enable refactoring of event names if needed
 * - Improve code readability and maintainability
 */
public final class CourseEventTypes {
    
    // Private constructor to prevent instantiation
    private CourseEventTypes() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Event type for course definition.
     */
    public static final String COURSE_DEFINED = "CourseDefined";
    
    /**
     * Event type for course capacity changes.
     */
    public static final String COURSE_CAPACITY_CHANGED = "CourseCapacityChanged";
    
    /**
     * Event type for student subscription to a course.
     * This event has tags for both course_id and student_id (multi-entity event).
     */
    public static final String STUDENT_SUBSCRIBED_TO_COURSE = "StudentSubscribedToCourse";
}

