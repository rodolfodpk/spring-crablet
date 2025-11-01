package com.crablet.examples.courses.domain.projections;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.courses.domain.event.CourseDefined;

/**
 * Projector for course existence check.
 * Not a singleton - create instances as needed. This class is stateless and thread-safe.
 */
public class CourseExistsProjection implements StateProjector<Boolean> {

    public CourseExistsProjection() {
    }

    @Override
    public String getId() {
        return "course-exists-projector";
    }

    @Override
    public java.util.List<String> getEventTypes() {
        return java.util.List.of("CourseDefined");
    }

    @Override
    public Boolean getInitialState() {
        return false;
    }

    @Override
    public Boolean transition(Boolean currentState, StoredEvent event, EventDeserializer context) {
        // CourseDefined event means course exists
        context.deserialize(event, CourseDefined.class);
        return true;
    }
}

