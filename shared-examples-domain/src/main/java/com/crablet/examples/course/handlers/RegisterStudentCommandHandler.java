package com.crablet.examples.course.handlers;

import com.crablet.command.CommandDecision;
import com.crablet.command.IdempotentCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.examples.course.commands.RegisterStudentCommand;
import com.crablet.examples.course.events.StudentRegistered;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.course.CourseTags.STUDENT_ID;

/**
 * Command handler for registering students.
 * <p>
 * DCB Principle: Idempotent operation — student registration must succeed exactly once per student_id.
 */
@Component
public class RegisterStudentCommandHandler implements IdempotentCommandHandler<RegisterStudentCommand> {

    public RegisterStudentCommandHandler() {
    }

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, RegisterStudentCommand command) {
        StudentRegistered studentRegistered = StudentRegistered.of(command.studentId());

        AppendEvent event = AppendEvent.builder(type(StudentRegistered.class))
                .tag(STUDENT_ID, command.studentId())
                .data(studentRegistered)
                .build();

        return CommandDecision.Idempotent.of(event, type(StudentRegistered.class), STUDENT_ID, command.studentId());
    }
}
