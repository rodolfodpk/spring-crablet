package com.crablet.command.handlers.courses.unit;

import com.crablet.test.commands.AbstractInMemoryHandlerTest;
import com.crablet.examples.course.commands.RegisterStudentCommand;
import com.crablet.examples.course.events.StudentRegistered;
import com.crablet.examples.course.handlers.RegisterStudentCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RegisterStudentCommandHandler}.
 */
@DisplayName("RegisterStudentCommandHandler Unit Tests")
class RegisterStudentCommandHandlerUnitTest extends AbstractInMemoryHandlerTest {

    private RegisterStudentCommandHandler handler;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new RegisterStudentCommandHandler();
    }

    @Test
    @DisplayName("Given no events, when registering student, then student registered event created")
    void givenNoEvents_whenRegisteringStudent_thenStudentRegisteredEventCreated() {
        // Given: No events (empty event store)

        // When
        RegisterStudentCommand command = RegisterStudentCommand.of("student1");
        List<Object> events = when(handler, command);

        // Then
        then(events, StudentRegistered.class, student -> {
            assertThat(student.studentId()).isEqualTo("student1");
        });
    }

    @Test
    @DisplayName("Given no events, when registering student, then event has correct tags")
    void givenNoEvents_whenRegisteringStudent_thenEventHasCorrectTags() {
        // Given: No events (empty event store)

        // When
        RegisterStudentCommand command = RegisterStudentCommand.of("student1");
        List<EventWithTags<Object>> events = whenWithTags(handler, command);

        // Then
        then(events, StudentRegistered.class, (student, tags) -> {
            assertThat(student.studentId()).isEqualTo("student1");
            assertThat(tags).containsEntry("student_id", "student1");
        });
    }
}
