package com.crablet.examples.courses.features.subscribe;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.command.CommandHandler;
import com.crablet.eventstore.command.CommandResult;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.courses.domain.CourseQueryPatterns;
import com.crablet.examples.courses.domain.event.StudentSubscribedToCourse;
import com.crablet.examples.courses.domain.exception.AlreadySubscribedException;
import com.crablet.examples.courses.domain.exception.CourseFullException;
import com.crablet.examples.courses.domain.exception.CourseNotFoundException;
import com.crablet.examples.courses.domain.exception.StudentSubscriptionLimitException;
import com.crablet.examples.courses.domain.projections.SubscriptionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.examples.courses.domain.CourseEventTypes.*;
import static com.crablet.examples.courses.domain.CourseTags.*;

/**
 * Command handler for subscribing students to courses.
 * <p>
 * DCB Principle: Uses multi-entity projection to enforce constraints on both
 * course (capacity) and student (subscription limit) entities atomically.
 * <p>
 * This demonstrates the key DCB pattern: a single event (StudentSubscribedToCourse)
 * has tags for both entities, enabling atomic constraint enforcement.
 */
@Component
public class SubscribeStudentToCourseCommandHandler implements CommandHandler<SubscribeStudentToCourseCommand> {

    private static final Logger log = LoggerFactory.getLogger(SubscribeStudentToCourseCommandHandler.class);
    
    /**
     * Maximum number of courses a student can subscribe to.
     * Following the DCB example, using 5 as the demo limit.
     */
    private static final int MAX_STUDENT_SUBSCRIPTIONS = 5;

    public SubscribeStudentToCourseCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, SubscribeStudentToCourseCommand command) {
        // Command is already validated at construction with YAVI

        // Use multi-entity decision model query
        Query decisionModel = CourseQueryPatterns.subscriptionDecisionModel(
                command.courseId(),
                command.studentId()
        );

        // Project state for BOTH entities with cursor
        SubscriptionStateProjector projector = new SubscriptionStateProjector(
                command.courseId(),
                command.studentId()
        );
        ProjectionResult<SubscriptionState> projection = eventStore.project(
                decisionModel, Cursor.zero(), SubscriptionState.class, List.of(projector));
        SubscriptionState state = projection.state();

        // Validate constraints
        if (!state.courseExists()) {
            log.warn("Subscription failed - course not found: courseId={}, studentId={}", 
                    command.courseId(), command.studentId());
            throw new CourseNotFoundException(command.courseId());
        }

        if (state.isCourseFull()) {
            log.warn("Subscription failed - course full: courseId={}, subscriptions={}, capacity={}", 
                    command.courseId(), state.courseSubscriptionsCount(), state.courseCapacity());
            throw new CourseFullException(command.courseId(), 
                    state.courseSubscriptionsCount(), state.courseCapacity());
        }

        if (state.studentAlreadySubscribed()) {
            log.warn("Subscription failed - already subscribed: studentId={}, courseId={}", 
                    command.studentId(), command.courseId());
            throw new AlreadySubscribedException(command.studentId(), command.courseId());
        }

        if (state.hasReachedSubscriptionLimit(MAX_STUDENT_SUBSCRIPTIONS)) {
            log.warn("Subscription failed - student limit reached: studentId={}, subscriptions={}, max={}", 
                    command.studentId(), state.studentSubscriptionsCount(), MAX_STUDENT_SUBSCRIPTIONS);
            throw new StudentSubscriptionLimitException(command.studentId(), 
                    state.studentSubscriptionsCount(), MAX_STUDENT_SUBSCRIPTIONS);
        }

        // Create event with BOTH tags (multi-entity event)
        StudentSubscribedToCourse subscription = StudentSubscribedToCourse.of(
                command.studentId(),
                command.courseId()
        );

        AppendEvent event = AppendEvent.builder(STUDENT_SUBSCRIBED_TO_COURSE)
                .tag(COURSE_ID, command.courseId())
                .tag(STUDENT_ID, command.studentId())
                .data(subscription)
                .build();

        // Subscriptions are non-commutative - order matters for both course and student constraints
        // DCB cursor check REQUIRED: prevents concurrent subscriptions exceeding limits
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }

    @Override
    public String getCommandType() {
        return "subscribe_student_to_course";
    }

    /**
     * Composite projector for subscription state (all constraints).
     * Not a singleton - create instances as needed per student/course pair.
     */
    static class SubscriptionStateProjector implements com.crablet.eventstore.query.StateProjector<SubscriptionState> {
        private final String courseId;
        private final String studentId;
        

        public SubscriptionStateProjector(String courseId, String studentId) {
            this.courseId = courseId;
            this.studentId = studentId;
        }

        @Override
        public String getId() {
            return "subscription-state-projector-" + courseId + "-" + studentId;
        }

        @Override
        public java.util.List<String> getEventTypes() {
            return java.util.List.of("CourseDefined", "CourseCapacityChanged", "StudentSubscribedToCourse");
        }

        @Override
        public SubscriptionState getInitialState() {
            return new SubscriptionState(false, 0, 0, 0, false);
        }

        @Override
        public SubscriptionState transition(SubscriptionState current, com.crablet.eventstore.store.StoredEvent event, 
                                             com.crablet.eventstore.query.EventDeserializer context) {
            return switch (event.type()) {
                case "CourseDefined" -> {
                    com.crablet.examples.courses.domain.event.CourseDefined courseDefined = 
                            context.deserialize(event, com.crablet.examples.courses.domain.event.CourseDefined.class);
                    if (courseDefined.courseId().equals(courseId)) {
                        yield new SubscriptionState(
                                true, // course exists
                                courseDefined.capacity(),
                                current.courseSubscriptionsCount(),
                                current.studentSubscriptionsCount(),
                                current.studentAlreadySubscribed()
                        );
                    }
                    yield current;
                }
                case "CourseCapacityChanged" -> {
                    com.crablet.examples.courses.domain.event.CourseCapacityChanged capacityChanged = 
                            context.deserialize(event, com.crablet.examples.courses.domain.event.CourseCapacityChanged.class);
                    if (capacityChanged.courseId().equals(courseId)) {
                        yield new SubscriptionState(
                                current.courseExists(),
                                capacityChanged.newCapacity(),
                                current.courseSubscriptionsCount(),
                                current.studentSubscriptionsCount(),
                                current.studentAlreadySubscribed()
                        );
                    }
                    yield current;
                }
                case "StudentSubscribedToCourse" -> {
                    com.crablet.examples.courses.domain.event.StudentSubscribedToCourse subscription = 
                            context.deserialize(event, com.crablet.examples.courses.domain.event.StudentSubscribedToCourse.class);
                    
                    boolean affectsCourse = subscription.courseId().equals(courseId);
                    boolean affectsStudent = subscription.studentId().equals(studentId);
                    
                    int newCourseSubscriptions = affectsCourse ? 
                            current.courseSubscriptionsCount() + 1 : current.courseSubscriptionsCount();
                    int newStudentSubscriptions = affectsStudent ? 
                            current.studentSubscriptionsCount() + 1 : current.studentSubscriptionsCount();
                    boolean newAlreadySubscribed = current.studentAlreadySubscribed() || 
                            (affectsCourse && affectsStudent);
                    
                    yield new SubscriptionState(
                            current.courseExists(),
                            current.courseCapacity(),
                            newCourseSubscriptions,
                            newStudentSubscriptions,
                            newAlreadySubscribed
                    );
                }
                default -> current;
            };
        }
    }
}

