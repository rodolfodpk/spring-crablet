package com.crablet.command.handlers.courses;

import com.crablet.command.CommandDecision;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StoredEvent;
import com.crablet.examples.course.commands.SubscribeStudentToCourseCommand;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.events.StudentRegistered;
import com.crablet.examples.course.events.StudentSubscribedToCourse;
import com.crablet.examples.course.exceptions.AlreadySubscribedException;
import com.crablet.examples.course.exceptions.CourseFullException;
import com.crablet.examples.course.exceptions.CourseNotFoundException;
import com.crablet.examples.course.exceptions.StudentSubscriptionLimitException;
import com.crablet.examples.course.handlers.SubscribeStudentToCourseCommandHandler;
import com.crablet.test.AbstractCrabletTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for SubscribeStudentToCourseCommandHandler.
 * <p>
 * DCB Principle: Tests verify multi-entity constraint enforcement.
 */
@DisplayName("SubscribeStudentToCourseCommandHandler Integration Tests")
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class SubscribeStudentToCourseCommandHandlerTest extends AbstractCrabletTest {

    private SubscribeStudentToCourseCommandHandler handler;
    
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;
    
    private CourseTestUtils courseTestUtils;

    @BeforeEach
    void setUp() {
        handler = new SubscribeStudentToCourseCommandHandler();
        courseTestUtils = new CourseTestUtils(objectMapper);
    }

    @Test
    @DisplayName("Should successfully subscribe student to course")
    void testHandleSubscribe_Success() {
        // Arrange - create course and student
        appendCourseDefined("c1", 10);
        appendStudentRegistered("s1");

        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "c1");

        // Act
        CommandDecision result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).type()).isEqualTo("StudentSubscribedToCourse");
        assertThat(result.events().get(0).tags()).hasSize(2); // course_id and student_id
        StudentSubscribedToCourse subscription = courseTestUtils.deserializeEventData(
                result.events().get(0).eventData(), StudentSubscribedToCourse.class);
        assertThat(subscription.studentId()).isEqualTo("s1");
        assertThat(subscription.courseId()).isEqualTo("c1");
    }

    @Test
    @DisplayName("Should throw exception when course does not exist")
    void testHandleSubscribe_CourseNotFound() {
        // Arrange
        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "nonexistent");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(CourseNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should throw exception when course is full")
    void testHandleSubscribe_CourseFull() {
        // Arrange - create course with capacity 3 and subscribe 3 students
        appendCourseDefined("c1", 3);

        // Subscribe 3 students (fill the course)
        for (int i = 1; i <= 3; i++) {
            appendStudentRegistered("s" + i);
            SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s" + i, "c1");
            CommandDecision result = handler.handle(eventStore, cmd);
            CommandDecision.NonCommutative nc = (CommandDecision.NonCommutative) result;
            eventStore.appendNonCommutative(nc.events(), nc.decisionModel(), nc.streamPosition());
        }

        // Try to subscribe 4th student
        appendStudentRegistered("s4");
        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s4", "c1");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(CourseFullException.class);
    }

    @Test
    @DisplayName("Should throw exception when student already subscribed to course")
    void testHandleSubscribe_AlreadySubscribed() {
        // Arrange - create course and subscribe student
        appendCourseDefined("c1", 10);
        appendStudentRegistered("s1");

        SubscribeStudentToCourseCommand firstCmd = SubscribeStudentToCourseCommand.of("s1", "c1");
        CommandDecision firstResult = handler.handle(eventStore, firstCmd);
        CommandDecision.NonCommutative firstNc = (CommandDecision.NonCommutative) firstResult;
        eventStore.appendNonCommutative(firstNc.events(), firstNc.decisionModel(), firstNc.streamPosition());

        // Try to subscribe same student again
        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "c1");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(AlreadySubscribedException.class);
    }

    @Test
    @DisplayName("Should throw exception when student reaches subscription limit")
    void testHandleSubscribe_StudentLimitReached() {
        // Arrange - create 5 courses and subscribe student to all
        appendStudentRegistered("s1");
        for (int i = 1; i <= 5; i++) {
            appendCourseDefined("c" + i, 10);

            SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "c" + i);
            CommandDecision result = handler.handle(eventStore, cmd);
            CommandDecision.NonCommutative nc = (CommandDecision.NonCommutative) result;
            eventStore.appendNonCommutative(nc.events(), nc.decisionModel(), nc.streamPosition());
        }

        // Create 6th course
        appendCourseDefined("c6", 10);

        // Try to subscribe to 6th course (exceeds limit of 5)
        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "c6");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(StudentSubscriptionLimitException.class);
    }

    private void appendCourseDefined(String courseId, int capacity) {
        CourseDefined courseDefined = CourseDefined.of(courseId, capacity);
        StoredEvent courseEvent = courseTestUtils.createEvent(courseDefined);
        AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                .data(courseEvent.data())
                .tag("course_id", courseId)
                .build();
        eventStore.appendCommutative(List.of(courseInputEvent));
    }

    private void appendStudentRegistered(String studentId) {
        StudentRegistered studentRegistered = StudentRegistered.of(studentId);
        StoredEvent studentEvent = courseTestUtils.createEvent(studentRegistered);
        AppendEvent studentInputEvent = AppendEvent.builder(studentEvent.type())
                .data(studentEvent.data())
                .tag("student_id", studentId)
                .build();
        eventStore.appendCommutative(List.of(studentInputEvent));
    }
}
