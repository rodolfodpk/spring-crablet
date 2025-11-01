package com.crablet.examples.courses.domain.projections;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.courses.domain.event.StudentSubscribedToCourse;

/**
 * Projector for checking if student is already subscribed to a specific course.
 * Not a singleton - create instances as needed. This class is stateless and thread-safe.
 */
public class StudentAlreadySubscribedProjection implements StateProjector<Boolean> {

    private final String studentId;
    private final String courseId;

    public StudentAlreadySubscribedProjection(String studentId, String courseId) {
        this.studentId = studentId;
        this.courseId = courseId;
    }

    @Override
    public String getId() {
        return "student-already-subscribed-projector-" + studentId + "-" + courseId;
    }

    @Override
    public java.util.List<String> getEventTypes() {
        return java.util.List.of("StudentSubscribedToCourse");
    }

    @Override
    public Boolean getInitialState() {
        return false;
    }

    @Override
    public Boolean transition(Boolean currentState, StoredEvent event, EventDeserializer context) {
        StudentSubscribedToCourse subscription = context.deserialize(event, StudentSubscribedToCourse.class);
        // Check if this subscription matches both student and course
        if (subscription.studentId().equals(studentId) && subscription.courseId().equals(courseId)) {
            return true;
        }
        return currentState;
    }
}

