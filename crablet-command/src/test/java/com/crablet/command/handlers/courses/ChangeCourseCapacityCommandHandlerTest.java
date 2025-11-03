package com.crablet.command.handlers.courses;

import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandResult;
import com.crablet.examples.courses.features.changecapacity.ChangeCourseCapacityCommand;
import com.crablet.command.handlers.ChangeCourseCapacityCommandHandler;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.courses.domain.event.CourseDefined;
import com.crablet.examples.courses.domain.event.CourseCapacityChanged;
import com.crablet.examples.courses.domain.exception.CourseNotFoundException;
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
 * Integration tests for ChangeCourseCapacityCommandHandler.
 */
@DisplayName("ChangeCourseCapacityCommandHandler Integration Tests")
class ChangeCourseCapacityCommandHandlerTest extends AbstractCrabletTest {

    private ChangeCourseCapacityCommandHandler handler;
    
    @Autowired
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        handler = new ChangeCourseCapacityCommandHandler();
    }

    @Test
    @DisplayName("Should successfully change course capacity")
    void testHandleChangeCapacity_Success() {
        // Arrange - create course first
        CourseDefined courseDefined = CourseDefined.of("c1", 10);
        StoredEvent courseEvent = CourseTestUtils.createEvent(courseDefined);
        AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                .data(courseEvent.data())
                .tag("course_id", "c1")
                .build();
        eventStore.append(List.of(courseInputEvent));

        ChangeCourseCapacityCommand cmd = ChangeCourseCapacityCommand.of("c1", 15);

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        CourseCapacityChanged capacityChanged = CourseTestUtils.deserializeEventData(
                result.events().get(0).eventData(), CourseCapacityChanged.class);
        assertThat(capacityChanged.courseId()).isEqualTo("c1");
        assertThat(capacityChanged.newCapacity()).isEqualTo(15);
    }

    @Test
    @DisplayName("Should throw exception when course does not exist")
    void testHandleChangeCapacity_CourseNotFound() {
        // Arrange
        ChangeCourseCapacityCommand cmd = ChangeCourseCapacityCommand.of("nonexistent", 15);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(CourseNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should throw exception when new capacity equals current capacity")
    void testHandleChangeCapacity_SameCapacity() {
        // Arrange - create course with capacity 10
        CourseDefined courseDefined = CourseDefined.of("c1", 10);
        StoredEvent courseEvent = CourseTestUtils.createEvent(courseDefined);
        AppendEvent courseInputEvent = AppendEvent.builder(courseEvent.type())
                .data(courseEvent.data())
                .tag("course_id", "c1")
                .build();
        eventStore.append(List.of(courseInputEvent));

        ChangeCourseCapacityCommand cmd = ChangeCourseCapacityCommand.of("c1", 10);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same as the current capacity");
    }
}

