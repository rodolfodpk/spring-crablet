package com.crablet.command.handlers.courses;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.examples.course.commands.DefineCourseCommand;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.course.events.CourseDefined;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.course.CourseTags.*;

/**
 * Command handler for defining courses.
 * <p>
 * DCB Principle: Uses idempotency check pattern (like OpenWallet).
 * Does not project state since only uniqueness check is required.
 */
@Component
public class DefineCourseCommandHandler implements CommandHandler<DefineCourseCommand> {

    public DefineCourseCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, DefineCourseCommand command) {
        // Command is already validated at construction with YAVI

        // Create event (optimistic - assume course doesn't exist)
        CourseDefined courseDefined = CourseDefined.of(
                command.courseId(),
                command.capacity()
        );

        AppendEvent event = AppendEvent.builder(type(CourseDefined.class))
                .tag(COURSE_ID, command.courseId())
                .data(courseDefined)
                .build();

        // Build condition to enforce uniqueness using DCB idempotency pattern
        // Fails if ANY CourseDefined event exists for this course_id (idempotency check)
        // No concurrency check needed for course creation - only idempotency matters
        AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
                .withIdempotencyCheck(type(CourseDefined.class), COURSE_ID, command.courseId())
                .build();

        return CommandResult.of(List.of(event), condition);
    }
}

