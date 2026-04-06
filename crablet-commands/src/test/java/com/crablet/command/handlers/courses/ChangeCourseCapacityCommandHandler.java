package com.crablet.command.handlers.courses;

import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import com.crablet.examples.course.CourseQueryPatterns;
import com.crablet.examples.course.commands.ChangeCourseCapacityCommand;
import com.crablet.examples.course.events.CourseCapacityChanged;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.exceptions.CourseNotFoundException;
import com.crablet.examples.course.projections.CourseCapacityProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.course.CourseTags.COURSE_ID;

/**
 * Command handler for changing course capacity.
 * <p>
 * DCB Principle: Non-commutative operation — stream position check ensures course capacity
 * hasn't changed since we read it, preventing concurrent capacity changes.
 */
@Component
public class ChangeCourseCapacityCommandHandler implements NonCommutativeCommandHandler<ChangeCourseCapacityCommand> {

    private static final Logger log = LoggerFactory.getLogger(ChangeCourseCapacityCommandHandler.class);

    public ChangeCourseCapacityCommandHandler() {
    }

    @Override
    public Decision decide(EventStore eventStore, ChangeCourseCapacityCommand command) {
        // Command is already validated at construction with YAVI

        Query decisionModel = CourseQueryPatterns.courseDecisionModel(command.courseId());

        CourseStateProjector projector = new CourseStateProjector();
        ProjectionResult<CourseState> projection = eventStore.project(
                decisionModel, StreamPosition.zero(), CourseState.class, List.of(projector));
        CourseState state = projection.state();

        if (!state.courseExists()) {
            log.warn("Change capacity failed - course not found: courseId={}", command.courseId());
            throw new CourseNotFoundException(command.courseId());
        }

        if (state.courseCapacity() == command.newCapacity()) {
            throw new IllegalArgumentException("New capacity " + command.newCapacity() +
                    " is the same as the current capacity");
        }

        CourseCapacityChanged capacityChanged = CourseCapacityChanged.of(
                command.courseId(),
                command.newCapacity()
        );

        AppendEvent event = AppendEvent.builder(type(CourseCapacityChanged.class))
                .tag(COURSE_ID, command.courseId())
                .data(capacityChanged)
                .build();

        return new Decision(List.of(event), decisionModel, projection.streamPosition());
    }

    record CourseState(boolean courseExists, int courseCapacity) {}

    static class CourseStateProjector implements StateProjector<CourseState> {
        private final CourseCapacityProjection capacityProjection = new CourseCapacityProjection();

        @Override
        public String getId() {
            return "course-state-projector";
        }

        @Override
        public List<String> getEventTypes() {
            return List.of(
                    type(CourseDefined.class),
                    type(CourseCapacityChanged.class)
            );
        }

        @Override
        public CourseState getInitialState() {
            return new CourseState(false, 0);
        }

        @Override
        public CourseState transition(CourseState current, StoredEvent event, EventDeserializer context) {
            boolean exists = current.courseExists() || event.type().equals(type(CourseDefined.class));
            Integer capacity = capacityProjection.transition(current.courseCapacity(), event, context);
            return new CourseState(exists, capacity);
        }
    }
}
