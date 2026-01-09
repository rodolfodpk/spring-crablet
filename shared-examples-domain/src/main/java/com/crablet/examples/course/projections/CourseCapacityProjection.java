package com.crablet.examples.course.projections;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.course.events.CourseEvent;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.events.CourseCapacityChanged;
import com.crablet.examples.course.events.StudentSubscribedToCourse;

/**
 * Projector for course capacity.
 * Not a singleton - create instances as needed. This class is stateless and thread-safe.
 */
public class CourseCapacityProjection implements StateProjector<Integer> {

    public CourseCapacityProjection() {
    }

    @Override
    public String getId() {
        return "course-capacity-projector";
    }

    @Override
    public java.util.List<String> getEventTypes() {
        return java.util.List.of("CourseDefined", "CourseCapacityChanged");
    }

    @Override
    public Integer getInitialState() {
        return 0;
    }

    @Override
    public Integer transition(Integer currentState, StoredEvent event, EventDeserializer context) {
        // Deserialize to sealed interface for type-safe pattern matching
        CourseEvent courseEvent = context.deserialize(event, CourseEvent.class);
        
        // Use pattern matching instead of string-based switch
        return switch (courseEvent) {
            case CourseDefined e -> e.capacity();
            case CourseCapacityChanged e -> e.newCapacity();
            case StudentSubscribedToCourse _ -> currentState; // Subscription doesn't change capacity
        };
    }
}

