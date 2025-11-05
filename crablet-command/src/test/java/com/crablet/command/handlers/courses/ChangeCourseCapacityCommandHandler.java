package com.crablet.command.handlers.courses;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.examples.courses.features.changecapacity.ChangeCourseCapacityCommand;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.courses.domain.CourseQueryPatterns;
import com.crablet.examples.courses.domain.event.CourseCapacityChanged;
import com.crablet.examples.courses.domain.exception.CourseNotFoundException;
import com.crablet.examples.courses.domain.projections.CourseExistsProjection;
import com.crablet.examples.courses.domain.projections.CourseCapacityProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.examples.courses.domain.CourseEventTypes.*;
import static com.crablet.examples.courses.domain.CourseTags.*;

/**
 * Command handler for changing course capacity.
 * <p>
 * DCB Principle: Uses cursor check pattern to ensure course capacity hasn't changed
 * since we read it. Prevents race conditions when capacity is changed concurrently.
 */
@Component
public class ChangeCourseCapacityCommandHandler implements CommandHandler<ChangeCourseCapacityCommand> {

    private static final Logger log = LoggerFactory.getLogger(ChangeCourseCapacityCommandHandler.class);

    public ChangeCourseCapacityCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, ChangeCourseCapacityCommand command) {
        // Command is already validated at construction with YAVI

        // Use decision model query
        Query decisionModel = CourseQueryPatterns.courseDecisionModel(command.courseId());

        // Project state with cursor - use composite projector
        CourseStateProjector projector = new CourseStateProjector();
        ProjectionResult<CourseState> projection = eventStore.project(
                decisionModel, Cursor.zero(), CourseState.class, List.of(projector));
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

        AppendEvent event = AppendEvent.builder(COURSE_CAPACITY_CHANGED)
                .tag(COURSE_ID, command.courseId())
                .data(capacityChanged)
                .build();

        // Capacity changes are non-commutative - order matters
        // DCB cursor check REQUIRED: prevents concurrent capacity changes
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }

    /**
     * Minimal state for capacity change operations.
     */
    record CourseState(
            boolean courseExists,
            int courseCapacity
    ) {
    }

    /**
     * Composite projector for course state (existence + capacity).
     * Not a singleton - create instances as needed. This class is stateless and thread-safe.
     */
    static class CourseStateProjector implements com.crablet.eventstore.query.StateProjector<CourseState> {
        private final CourseExistsProjection existsProjection = new CourseExistsProjection();
        private final CourseCapacityProjection capacityProjection = new CourseCapacityProjection();

        @Override
        public String getId() {
            return "course-state-projector";
        }

        @Override
        public java.util.List<String> getEventTypes() {
            return java.util.List.of("CourseDefined", "CourseCapacityChanged");
        }

        @Override
        public CourseState getInitialState() {
            return new CourseState(false, 0);
        }

        @Override
        public CourseState transition(CourseState current, com.crablet.eventstore.store.StoredEvent event, 
                                      com.crablet.eventstore.query.EventDeserializer context) {
            Boolean exists = existsProjection.transition(current.courseExists(), event, context);
            Integer capacity = capacityProjection.transition(current.courseCapacity(), event, context);
            return new CourseState(exists, capacity);
        }
    }
}

