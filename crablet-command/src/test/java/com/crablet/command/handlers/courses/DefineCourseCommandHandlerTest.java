package com.crablet.command.handlers.courses;

import com.crablet.command.CommandResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.course.commands.DefineCourseCommand;
import com.crablet.examples.course.events.CourseDefined;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DefineCourseCommandHandler.
 * <p>
 * DCB Principle: Tests verify idempotency check pattern.
 */
@DisplayName("DefineCourseCommandHandler Integration Tests")
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class DefineCourseCommandHandlerTest extends AbstractCrabletTest {

    private DefineCourseCommandHandler handler;
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    
    private CourseTestUtils courseTestUtils;

    @BeforeEach
    void setUp() {
        handler = new DefineCourseCommandHandler();
        courseTestUtils = new CourseTestUtils(objectMapper);
    }

    @Test
    @DisplayName("Should successfully define a new course")
    void testHandleDefineCourse_Success() {
        // Arrange
        DefineCourseCommand cmd = DefineCourseCommand.of("c1", 10);

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo("CourseDefined");
                    assertThat(event.tags()).hasSize(1);
                    assertThat(event.tags().get(0).key()).isEqualTo("course_id");
                    assertThat(event.tags().get(0).value()).isEqualTo("c1");
                });

        CourseDefined courseDefined = courseTestUtils.deserializeEventData(result.events().get(0).eventData(), CourseDefined.class);
        assertThat(courseDefined)
                .satisfies(c -> {
                    assertThat(c.courseId()).isEqualTo("c1");
                    assertThat(c.capacity()).isEqualTo(10);
                });
    }

    @Test
    @DisplayName("Should throw exception when defining course with existing ID")
    void testHandleDefineCourse_DuplicateId() {
        // Arrange - create course first
        CourseDefined existing = CourseDefined.of("c1", 5);
        StoredEvent existingEvent = courseTestUtils.createEvent(existing);
        AppendEvent existingInputEvent = AppendEvent.builder(existingEvent.type())
                .data(existingEvent.data())
                .tag("course_id", "c1")
                .build();
        eventStore.appendIf(List.of(existingInputEvent), AppendCondition.empty());

        DefineCourseCommand cmd = DefineCourseCommand.of("c1", 10);

        // Act & Assert - idempotency check should prevent duplicate
        // Note: The handler returns CommandResult with idempotency check,
        // but appendIf will throw ConcurrencyException if course exists
        CommandResult result = handler.handle(eventStore, cmd);
        assertThatThrownBy(() -> eventStore.appendIf(result.events(), result.appendCondition()))
                .isInstanceOf(com.crablet.eventstore.dcb.ConcurrencyException.class);
    }

    @Test
    @DisplayName("Should throw exception for zero capacity at command creation")
    void testHandleDefineCourse_ZeroCapacity() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> DefineCourseCommand.of("c1", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    @DisplayName("Should throw exception for negative capacity at command creation")
    void testHandleDefineCourse_NegativeCapacity() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> DefineCourseCommand.of("c1", -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }
}

