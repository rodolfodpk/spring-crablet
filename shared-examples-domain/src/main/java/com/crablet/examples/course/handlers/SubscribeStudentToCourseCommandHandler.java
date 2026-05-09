package com.crablet.examples.course.handlers;

import com.crablet.command.CommandDecision;
import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.examples.course.CourseQueryPatterns;
import com.crablet.examples.course.commands.SubscribeStudentToCourseCommand;
import com.crablet.examples.course.events.CourseCapacityChanged;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.events.StudentRegistered;
import com.crablet.examples.course.events.StudentSubscribedToCourse;
import com.crablet.examples.course.exceptions.AlreadySubscribedException;
import com.crablet.examples.course.exceptions.CourseFullException;
import com.crablet.examples.course.exceptions.CourseNotFoundException;
import com.crablet.examples.course.exceptions.StudentNotFoundException;
import com.crablet.examples.course.exceptions.StudentSubscriptionLimitException;
import com.crablet.examples.course.projections.SubscriptionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.course.CourseTags.COURSE_ID;
import static com.crablet.examples.course.CourseTags.STUDENT_ID;

/**
 * Command handler for subscribing students to courses.
 * <p>
 * DCB Principle: Non-commutative operation — order matters for both course capacity
 * and student subscription limit constraints. One decision model spans both the course
 * and the student, enforcing multi-aggregate consistency in a single boundary.
 */
@Component
public class SubscribeStudentToCourseCommandHandler implements NonCommutativeCommandHandler<SubscribeStudentToCourseCommand> {

    private static final Logger log = LoggerFactory.getLogger(SubscribeStudentToCourseCommandHandler.class);

    private static final int MAX_STUDENT_SUBSCRIPTIONS = 5;

    public SubscribeStudentToCourseCommandHandler() {
    }

    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, SubscribeStudentToCourseCommand command) {
        Query decisionModel = CourseQueryPatterns.subscriptionDecisionModel(
                command.courseId(),
                command.studentId()
        );

        ProjectionResult<SubscriptionState> projection = eventStore.project(
                decisionModel, StreamPosition.zero(), SubscriptionState.class,
                List.of(subscriptionStateProjector(command.courseId(), command.studentId())));
        SubscriptionState state = projection.state();

        if (!state.courseExists()) {
            log.warn("Subscription failed - course not found: courseId={}, studentId={}",
                    command.courseId(), command.studentId());
            throw new CourseNotFoundException(command.courseId());
        }

        if (!state.studentExists()) {
            log.warn("Subscription failed - student not found: courseId={}, studentId={}",
                    command.courseId(), command.studentId());
            throw new StudentNotFoundException(command.studentId());
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

        StudentSubscribedToCourse subscription = StudentSubscribedToCourse.of(
                command.studentId(),
                command.courseId()
        );

        AppendEvent event = AppendEvent.builder(type(StudentSubscribedToCourse.class))
                .tag(COURSE_ID, command.courseId())
                .tag(STUDENT_ID, command.studentId())
                .data(subscription)
                .build();

        return CommandDecision.NonCommutative.of(event, decisionModel, projection.streamPosition());
    }

    private static StateProjector<SubscriptionState> subscriptionStateProjector(
            String courseId, String studentId) {
        return StateProjector.<SubscriptionState>builder(
                        "subscription-state-projector-" + courseId + "-" + studentId,
                        SubscriptionState.initial())
                .on(CourseDefined.class, (state, event) ->
                        event.courseId().equals(courseId) ? state.withCourse(event.capacity()) : state)
                .on(CourseCapacityChanged.class, (state, event) ->
                        event.courseId().equals(courseId) ? state.withCapacity(event.newCapacity()) : state)
                .on(StudentRegistered.class, (state, event) ->
                        event.studentId().equals(studentId) ? state.withStudentExists() : state)
                .on(StudentSubscribedToCourse.class, (state, event) ->
                        state.applySubscription(
                                event.courseId().equals(courseId),
                                event.studentId().equals(studentId)))
                .build();
    }
}
