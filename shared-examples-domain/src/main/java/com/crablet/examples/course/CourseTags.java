package com.crablet.examples.course;

/**
 * Constants for course domain tag names.
 * 
 * This class provides centralized tag name constants to:
 * - Prevent typos in tag names
 * - Make tag names easily discoverable
 * - Enable refactoring of tag names if needed
 * - Improve code readability and maintainability
 */
public final class CourseTags {
    
    // Private constructor to prevent instantiation
    private CourseTags() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Tag for course ID - used across all course operations.
     * This is the primary identifier for course-related events.
     */
    public static final String COURSE_ID = "course_id";
    
    /**
     * Tag for student ID - used in subscription operations.
     * This tag enables multi-entity queries for student constraints.
     */
    public static final String STUDENT_ID = "student_id";
}

