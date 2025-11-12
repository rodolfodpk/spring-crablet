package com.crablet.command.handlers.courses.unit;

import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import com.crablet.command.handlers.courses.DefineCourseCommandHandler;
import com.crablet.examples.courses.domain.event.CourseDefined;
import com.crablet.examples.courses.features.definecourse.DefineCourseCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefineCourseCommandHandler}.
 * <p>
 * These tests focus on business logic validation and happy paths.
 * DCB concurrency (idempotency) is tested in integration tests.
 */
@DisplayName("DefineCourseCommandHandler Unit Tests")
class DefineCourseCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private DefineCourseCommandHandler handler;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new DefineCourseCommandHandler();
    }
    
    @Test
    @DisplayName("Given no events, when defining course, then course defined event created")
    void givenNoEvents_whenDefiningCourse_thenCourseDefinedEventCreated() {
        // Given: No events (empty event store)
        
        // When
        DefineCourseCommand command = DefineCourseCommand.of("course1", 50);
        List<Object> events = when(handler, command);
        
        // Then
        then(events, CourseDefined.class, course -> {
            assertThat(course.courseId()).isEqualTo("course1");
            assertThat(course.capacity()).isEqualTo(50);
        });
    }
    
    @Test
    @DisplayName("Given no events, when defining course, then event has correct tags")
    void givenNoEvents_whenDefiningCourse_thenEventHasCorrectTags() {
        // Given: No events (empty event store)
        
        // When - get events with tags
        DefineCourseCommand command = DefineCourseCommand.of("course1", 50);
        List<EventWithTags<Object>> events = whenWithTags(handler, command);
        
        // Then - verify event data AND tags
        then(events, CourseDefined.class, (course, tags) -> {
            // Event data
            assertThat(course.courseId()).isEqualTo("course1");
            assertThat(course.capacity()).isEqualTo(50);
            
            // Tags
            assertThat(tags).containsEntry("course_id", "course1");
        });
    }
}

