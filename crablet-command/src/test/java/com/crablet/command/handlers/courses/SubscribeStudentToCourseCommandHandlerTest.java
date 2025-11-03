package com.crablet.command.handlers.courses;

import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandResult;
import com.crablet.examples.courses.features.subscribe.SubscribeStudentToCourseCommand;
import com.crablet.command.handlers.SubscribeStudentToCourseCommandHandler;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.courses.domain.event.CourseDefined;
import com.crablet.examples.courses.domain.event.StudentSubscribedToCourse;
import com.crablet.examples.courses.domain.exception.AlreadySubscribedException;
import com.crablet.examples.courses.domain.exception.CourseFullException;
import com.crablet.examples.courses.domain.exception.CourseNotFoundException;
import com.crablet.examples.courses.domain.exception.StudentSubscriptionLimitException;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.examples.courses.testutils.CourseTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for SubscribeStudentToCourseCommandHandler.
 * <p>
 * DCB Principle: Tests verify multi-entity constraint enforcement.
 */
@DisplayName("SubscribeStudentToCourseCommandHandler Integration Tests")
class SubscribeStudentToCourseCommandHandlerTest extends AbstractCrabletTest {

    private SubscribeStudentToCourseCommandHandler handler;
    
    @Autowired
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        handler = new SubscribeStudentToCourseCommandHandler();
    }

    @Test
    @DisplayName("Should successfully subscribe student to course")
    void testHandleSubscribe_Success() {
        // Arrange - create course
        CourseDefined courseDefined = CourseDefined.of("c1", 10);
        StoredEvent courseEvent = CourseTestUtils.createEvent(courseDefined);
        AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                .data(courseEvent.data())
                .tag("course_id", "c1")
                .build();
        eventStore.append(List.of(courseInputEvent));

        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "c1");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).type()).isEqualTo("StudentSubscribedToCourse");
        assertThat(result.events().get(0).tags()).hasSize(2); // course_id and student_id
        StudentSubscribedToCourse subscription = CourseTestUtils.deserializeEventData(
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
        CourseDefined courseDefined = CourseDefined.of("c1", 3);
        StoredEvent courseEvent = CourseTestUtils.createEvent(courseDefined);
        AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                .data(courseEvent.data())
                .tag("course_id", "c1")
                .build();
        eventStore.append(List.of(courseInputEvent));

        // Subscribe 3 students (fill the course)
        for (int i = 1; i <= 3; i++) {
            SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s" + i, "c1");
            CommandResult result = handler.handle(eventStore, cmd);
            eventStore.appendIf(result.events(), result.appendCondition());
        }

        // Try to subscribe 4th student
        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s4", "c1");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(CourseFullException.class);
    }

    @Test
    @DisplayName("Should throw exception when student already subscribed to course")
    void testHandleSubscribe_AlreadySubscribed() {
        // Arrange - create course and subscribe student
        CourseDefined courseDefined = CourseDefined.of("c1", 10);
        StoredEvent courseEvent = CourseTestUtils.createEvent(courseDefined);
        AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                .data(courseEvent.data())
                .tag("course_id", "c1")
                .build();
        eventStore.append(List.of(courseInputEvent));

        SubscribeStudentToCourseCommand firstCmd = SubscribeStudentToCourseCommand.of("s1", "c1");
        CommandResult firstResult = handler.handle(eventStore, firstCmd);
        eventStore.appendIf(firstResult.events(), firstResult.appendCondition());

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
        for (int i = 1; i <= 5; i++) {
            CourseDefined courseDefined = CourseDefined.of("c" + i, 10);
            StoredEvent courseEvent = CourseTestUtils.createEvent(courseDefined);
            AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                    .data(courseEvent.data())
                    .tag("course_id", "c" + i)
                    .build();
            eventStore.append(List.of(courseInputEvent));

            SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "c" + i);
            CommandResult result = handler.handle(eventStore, cmd);
            eventStore.appendIf(result.events(), result.appendCondition());
        }

        // Create 6th course
        CourseDefined courseDefined = CourseDefined.of("c6", 10);
        StoredEvent courseEvent = CourseTestUtils.createEvent(courseDefined);
        AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                .data(courseEvent.data())
                .tag("course_id", "c6")
                .build();
        eventStore.append(List.of(courseInputEvent));

        // Try to subscribe to 6th course (exceeds limit of 5)
        SubscribeStudentToCourseCommand cmd = SubscribeStudentToCourseCommand.of("s1", "c6");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(StudentSubscriptionLimitException.class);
    }
}

