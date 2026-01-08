package com.crablet.examples.course.projections;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;

/**
 * Projector for counting student subscriptions.
 * Not a singleton - create instances as needed. This class is stateless and thread-safe.
 */
public class StudentSubscriptionsCountProjection implements StateProjector<Integer> {

    public StudentSubscriptionsCountProjection() {
    }

    @Override
    public String getId() {
        return "student-subscriptions-count-projector";
    }

    @Override
    public java.util.List<String> getEventTypes() {
        return java.util.List.of("StudentSubscribedToCourse");
    }

    @Override
    public Integer getInitialState() {
        return 0;
    }

    @Override
    public Integer transition(Integer currentState, StoredEvent event, EventDeserializer context) {
        // Each StudentSubscribedToCourse event increments the count
        return currentState + 1;
    }
}

