package com.crablet.command.handlers.courses;

import com.crablet.command.IdempotentCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.examples.course.commands.DefineCourseCommand;
import com.crablet.examples.course.events.CourseDefined;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.course.CourseTags.COURSE_ID;

/**
 * Command handler for defining courses.
 * <p>
 * DCB Principle: Idempotent operation — course creation must succeed exactly once per course_id.
 */
@Component
public class DefineCourseCommandHandler implements IdempotentCommandHandler<DefineCourseCommand> {

    public DefineCourseCommandHandler() {
    }

    @Override
    public Decision decide(EventStore eventStore, DefineCourseCommand command) {
        // Command is already validated at construction with YAVI

        CourseDefined courseDefined = CourseDefined.of(
                command.courseId(),
                command.capacity()
        );

        AppendEvent event = AppendEvent.builder(type(CourseDefined.class))
                .tag(COURSE_ID, command.courseId())
                .data(courseDefined)
                .build();

        // Idempotency: fails if ANY CourseDefined event exists for this course_id
        return new Decision(List.of(event), type(CourseDefined.class), COURSE_ID, command.courseId());
    }
}
