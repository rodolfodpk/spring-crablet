package com.crablet.examples.courses.domain.projections;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.courses.domain.event.CourseDefined;
import com.crablet.examples.courses.domain.event.CourseCapacityChanged;

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
        return switch (event.type()) {
            case "CourseDefined" -> {
                CourseDefined courseDefined = context.deserialize(event, CourseDefined.class);
                yield courseDefined.capacity();
            }
            case "CourseCapacityChanged" -> {
                CourseCapacityChanged capacityChanged = context.deserialize(event, CourseCapacityChanged.class);
                yield capacityChanged.newCapacity();
            }
            default -> currentState;
        };
    }
}

