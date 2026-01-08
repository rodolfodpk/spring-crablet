package com.crablet.examples.course.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for all course-related events.
 * This provides type safety and enables pattern matching.
 * No methods needed - pattern matching works on types, not methods.
 * Library code (EventDeserializer) works with this via Jackson polymorphic deserialization.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CourseDefined.class, name = "CourseDefined"),
        @JsonSubTypes.Type(value = CourseCapacityChanged.class, name = "CourseCapacityChanged"),
        @JsonSubTypes.Type(value = StudentSubscribedToCourse.class, name = "StudentSubscribedToCourse")
})
public sealed interface CourseEvent
        permits CourseDefined, CourseCapacityChanged, StudentSubscribedToCourse {
    // Empty interface - pattern matching works on types
}

