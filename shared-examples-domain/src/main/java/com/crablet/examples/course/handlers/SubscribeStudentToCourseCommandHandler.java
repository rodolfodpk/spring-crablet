package com.crablet.examples.course.handlers;

import com.crablet.command.CommandDecision;
import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.examples.course.CourseQueryPatterns;
import com.crablet.examples.course.commands.SubscribeStudentToCourseCommand;
import com.crablet.examples.course.events.CourseCapacityChanged;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.events.StudentSubscribedToCourse;
import com.crablet.examples.course.exceptions.AlreadySubscribedException;
import com.crablet.examples.course.exceptions.CourseFullException;
import com.crablet.examples.course.exceptions.CourseNotFoundException;
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

        SubscriptionStateProjector projector = new SubscriptionStateProjector(
                command.courseId(),
                command.studentId()
        );
        ProjectionResult<SubscriptionState> projection = eventStore.project(
                decisionModel, StreamPosition.zero(), SubscriptionState.class, List.of(projector));
        SubscriptionState state = projection.state();

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

    static class SubscriptionStateProjector implements StateProjector<SubscriptionState> {
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
        public List<String> getEventTypes() {
            return List.of(
                    type(CourseDefined.class),
                    type(CourseCapacityChanged.class),
                    type(StudentSubscribedToCourse.class)
            );
        }

        @Override
        public SubscriptionState getInitialState() {
            return new SubscriptionState(false, 0, 0, 0, false);
        }

        @Override
        public SubscriptionState transition(SubscriptionState current, StoredEvent event, EventDeserializer context) {
            return switch (event.type()) {
                case String s when s.equals(type(CourseDefined.class)) -> {
                    CourseDefined courseDefined = context.deserialize(event, CourseDefined.class);
                    if (courseDefined.courseId().equals(courseId)) {
                        yield new SubscriptionState(
                                true,
                                courseDefined.capacity(),
                                current.courseSubscriptionsCount(),
                                current.studentSubscriptionsCount(),
                                current.studentAlreadySubscribed()
                        );
                    }
                    yield current;
                }
                case String s when s.equals(type(CourseCapacityChanged.class)) -> {
                    CourseCapacityChanged capacityChanged = context.deserialize(event, CourseCapacityChanged.class);
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
                case String s when s.equals(type(StudentSubscribedToCourse.class)) -> {
                    StudentSubscribedToCourse subscription =
                            context.deserialize(event, StudentSubscribedToCourse.class);
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
