package com.crablet.examples.course;

import com.crablet.examples.course.commands.ChangeCourseCapacityCommand;
import com.crablet.examples.course.commands.DefineCourseCommand;
import com.crablet.examples.course.commands.SubscribeStudentToCourseCommand;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for all course-related commands.
 * This provides type safety and enables pattern matching.
 * No methods needed - pattern matching works on types, not methods.
 * Library code extracts command type from JSON via Jackson polymorphic serialization.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "commandType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DefineCourseCommand.class, name = "define_course"),
        @JsonSubTypes.Type(value = ChangeCourseCapacityCommand.class, name = "change_course_capacity"),
        @JsonSubTypes.Type(value = SubscribeStudentToCourseCommand.class, name = "subscribe_student_to_course")
})
public interface CourseCommand {
    // Empty interface - pattern matching works on types
}

