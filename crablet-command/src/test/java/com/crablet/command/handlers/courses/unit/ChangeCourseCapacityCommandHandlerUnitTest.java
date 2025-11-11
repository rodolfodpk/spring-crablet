package com.crablet.command.handlers.courses.unit;

import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import com.crablet.command.handlers.courses.ChangeCourseCapacityCommandHandler;
import com.crablet.examples.courses.domain.event.CourseCapacityChanged;
import com.crablet.examples.courses.domain.event.CourseDefined;
import com.crablet.examples.courses.domain.exception.CourseNotFoundException;
import com.crablet.examples.courses.features.changecapacity.ChangeCourseCapacityCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.crablet.examples.courses.domain.CourseEventTypes.COURSE_DEFINED;
import static com.crablet.examples.courses.domain.CourseEventTypes.COURSE_CAPACITY_CHANGED;
import static com.crablet.examples.courses.domain.CourseTags.COURSE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChangeCourseCapacityCommandHandler}.
 * <p>
 * These tests focus on business logic validation and happy paths.
 * DCB concurrency is tested in integration tests.
 */
@DisplayName("ChangeCourseCapacityCommandHandler Unit Tests")
class ChangeCourseCapacityCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private ChangeCourseCapacityCommandHandler handler;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new ChangeCourseCapacityCommandHandler();
    }
    
    @Test
    @DisplayName("Given course exists, when changing capacity, then capacity changed event created")
    void givenCourseExists_whenChangingCapacity_thenCapacityChangedEventCreated() {
        // Given
        given().event(COURSE_DEFINED, builder -> builder
            .data(CourseDefined.of("course1", 50))
            .tag(COURSE_ID, "course1")
        );
        
        // When
        ChangeCourseCapacityCommand command = ChangeCourseCapacityCommand.of("course1", 100);
        List<CourseCapacityChanged> events = when(handler, command, CourseCapacityChanged.class);
        
        // Then
        then(events, capacityChanged -> {
            assertThat(capacityChanged.courseId()).isEqualTo("course1");
            assertThat(capacityChanged.newCapacity()).isEqualTo(100);
        });
    }
    
    @Test
    @DisplayName("Given course does not exist, when changing capacity, then course not found exception")
    void givenCourseDoesNotExist_whenChangingCapacity_thenCourseNotFoundException() {
        // Given: No events (empty event store)
        
        // When & Then
        ChangeCourseCapacityCommand command = ChangeCourseCapacityCommand.of("course1", 100);
        assertThatThrownBy(() -> when(handler, command, CourseCapacityChanged.class))
            .isInstanceOf(CourseNotFoundException.class)
            .hasMessageContaining("course1");
    }
    
    @Test
    @DisplayName("Given course with same capacity, when changing capacity, then illegal argument exception")
    void givenCourseWithSameCapacity_whenChangingCapacity_thenIllegalArgumentException() {
        // Given
        given().event(COURSE_DEFINED, builder -> builder
            .data(CourseDefined.of("course1", 50))
            .tag(COURSE_ID, "course1")
        );
        
        // When & Then
        ChangeCourseCapacityCommand command = ChangeCourseCapacityCommand.of("course1", 50);
        assertThatThrownBy(() -> when(handler, command, CourseCapacityChanged.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same as the current capacity");
    }
    
    @Test
    @DisplayName("Given course with previous capacity changes, when changing capacity, then capacity changed event created")
    void givenCourseWithPreviousCapacityChanges_whenChangingCapacity_thenCapacityChangedEventCreated() {
        // Given
        given().event(COURSE_DEFINED, builder -> builder
            .data(CourseDefined.of("course1", 50))
            .tag(COURSE_ID, "course1")
        );
        given().event(COURSE_CAPACITY_CHANGED, builder -> builder
            .data(CourseCapacityChanged.of("course1", 75))
            .tag(COURSE_ID, "course1")
        );
        
        // When
        ChangeCourseCapacityCommand command = ChangeCourseCapacityCommand.of("course1", 100);
        List<CourseCapacityChanged> events = when(handler, command, CourseCapacityChanged.class);
        
        // Then
        then(events, capacityChanged -> {
            assertThat(capacityChanged.newCapacity()).isEqualTo(100);
        });
    }
}

