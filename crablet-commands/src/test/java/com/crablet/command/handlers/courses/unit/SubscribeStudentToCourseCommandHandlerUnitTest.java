package com.crablet.command.handlers.courses.unit;

import com.crablet.command.handlers.courses.SubscribeStudentToCourseCommandHandler;
import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import com.crablet.examples.course.commands.SubscribeStudentToCourseCommand;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.events.StudentSubscribedToCourse;
import com.crablet.examples.course.exceptions.AlreadySubscribedException;
import com.crablet.examples.course.exceptions.CourseFullException;
import com.crablet.examples.course.exceptions.CourseNotFoundException;
import com.crablet.examples.course.exceptions.StudentSubscriptionLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.course.CourseTags.COURSE_ID;
import static com.crablet.examples.course.CourseTags.STUDENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SubscribeStudentToCourseCommandHandler}.
 * <p>
 * These tests focus on business logic validation and happy paths.
 * DCB concurrency is tested in integration tests.
 */
@DisplayName("SubscribeStudentToCourseCommandHandler Unit Tests")
class SubscribeStudentToCourseCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private SubscribeStudentToCourseCommandHandler handler;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new SubscribeStudentToCourseCommandHandler();
    }
    
    @Test
    @DisplayName("Given course with available capacity, when subscribing student, then subscription event created")
    void givenCourseWithAvailableCapacity_whenSubscribingStudent_thenSubscriptionEventCreated() {
        // Given
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course1", 50))
            .tag(COURSE_ID, "course1")
        );
        
        // When
        SubscribeStudentToCourseCommand command = SubscribeStudentToCourseCommand.of("student1", "course1");
        List<Object> events = when(handler, command);
        
        // Then
        then(events, StudentSubscribedToCourse.class, subscription -> {
            assertThat(subscription.studentId()).isEqualTo("student1");
            assertThat(subscription.courseId()).isEqualTo("course1");
        });
    }
    
    @Test
    @DisplayName("Given course does not exist, when subscribing student, then course not found exception")
    void givenCourseDoesNotExist_whenSubscribingStudent_thenCourseNotFoundException() {
        // Given: No events (empty event store)
        
        // When & Then
        SubscribeStudentToCourseCommand command = SubscribeStudentToCourseCommand.of("student1", "course1");
        assertThatThrownBy(() -> when(handler, command))
            .isInstanceOf(CourseNotFoundException.class)
            .hasMessageContaining("course1");
    }
    
    @Test
    @DisplayName("Given course is full, when subscribing student, then course full exception")
    void givenCourseIsFull_whenSubscribingStudent_thenCourseFullException() {
        // Given - course with capacity 2, already 2 subscriptions
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course1", 2))
            .tag(COURSE_ID, "course1")
        );
        given().event(type(StudentSubscribedToCourse.class), builder -> builder
            .data(StudentSubscribedToCourse.of("student1", "course1"))
            .tag(COURSE_ID, "course1")
            .tag(STUDENT_ID, "student1")
        );
        given().event(type(StudentSubscribedToCourse.class), builder -> builder
            .data(StudentSubscribedToCourse.of("student2", "course1"))
            .tag(COURSE_ID, "course1")
            .tag(STUDENT_ID, "student2")
        );
        
        // When & Then
        SubscribeStudentToCourseCommand command = SubscribeStudentToCourseCommand.of("student3", "course1");
        assertThatThrownBy(() -> when(handler, command))
            .isInstanceOf(CourseFullException.class)
            .hasMessageContaining("course1");
    }
    
    @Test
    @DisplayName("Given student already subscribed, when subscribing again, then already subscribed exception")
    void givenStudentAlreadySubscribed_whenSubscribingAgain_thenAlreadySubscribedException() {
        // Given
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course1", 50))
            .tag(COURSE_ID, "course1")
        );
        given().event(type(StudentSubscribedToCourse.class), builder -> builder
            .data(StudentSubscribedToCourse.of("student1", "course1"))
            .tag(COURSE_ID, "course1")
            .tag(STUDENT_ID, "student1")
        );
        
        // When & Then
        SubscribeStudentToCourseCommand command = SubscribeStudentToCourseCommand.of("student1", "course1");
        assertThatThrownBy(() -> when(handler, command))
            .isInstanceOf(AlreadySubscribedException.class)
            .hasMessageContaining("student1")
            .hasMessageContaining("course1");
    }
    
    @Test
    @DisplayName("Given student reached subscription limit, when subscribing, then subscription limit exception")
    void givenStudentReachedSubscriptionLimit_whenSubscribing_thenSubscriptionLimitException() {
        // Given - student already subscribed to 5 courses (max limit)
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course1", 50))
            .tag(COURSE_ID, "course1")
        );
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course2", 50))
            .tag(COURSE_ID, "course2")
        );
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course3", 50))
            .tag(COURSE_ID, "course3")
        );
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course4", 50))
            .tag(COURSE_ID, "course4")
        );
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course5", 50))
            .tag(COURSE_ID, "course5")
        );
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course6", 50))
            .tag(COURSE_ID, "course6")
        );
        
        // Student subscribed to 5 courses
        for (int i = 1; i <= 5; i++) {
            final int courseNum = i;
            given().event(type(StudentSubscribedToCourse.class), builder -> builder
                .data(StudentSubscribedToCourse.of("student1", "course" + courseNum))
                .tag(COURSE_ID, "course" + courseNum)
                .tag(STUDENT_ID, "student1")
            );
        }
        
        // When & Then - try to subscribe to 6th course
        SubscribeStudentToCourseCommand command = SubscribeStudentToCourseCommand.of("student1", "course6");
        assertThatThrownBy(() -> when(handler, command))
            .isInstanceOf(StudentSubscriptionLimitException.class)
            .hasMessageContaining("student1");
    }
    
    @Test
    @DisplayName("Given valid subscription, when subscribing, then event has correct tags")
    void givenValidSubscription_whenSubscribing_thenEventHasCorrectTags() {
        // Given
        given().event(type(CourseDefined.class), builder -> builder
            .data(CourseDefined.of("course1", 50))
            .tag(COURSE_ID, "course1")
        );
        
        // When - get events with tags
        SubscribeStudentToCourseCommand command = SubscribeStudentToCourseCommand.of("student1", "course1");
        List<EventWithTags<Object>> events = whenWithTags(handler, command);
        
        // Then - verify event data AND tags
        then(events, StudentSubscribedToCourse.class, (subscription, tags) -> {
            // Event data
            assertThat(subscription.studentId()).isEqualTo("student1");
            assertThat(subscription.courseId()).isEqualTo("course1");
            
            // Tags (multi-entity event)
            assertThat(tags).containsEntry("course_id", "course1");
            assertThat(tags).containsEntry("student_id", "student1");
        });
    }
}

