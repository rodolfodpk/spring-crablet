package com.crablet.course.view.projectors;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.examples.course.events.CourseCapacityChanged;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.events.CourseEvent;
import com.crablet.examples.course.events.StudentSubscribedToCourse;
import com.crablet.views.AbstractTypedViewProjector;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;

/**
 * View projector for course availability (capacity vs. current enrollment).
 * Demonstrates multi-entity DCB: the same events that enforce constraints also drive the read model.
 */
@Component
public class CourseAvailabilityViewProjector extends AbstractTypedViewProjector<CourseEvent> {

    private static final String UPSERT_COURSE = """
        INSERT INTO course_availability (course_id, capacity, enrolled, updated_at)
        VALUES (?, ?, 0, ?)
        ON CONFLICT (course_id)
        DO UPDATE SET
            capacity   = EXCLUDED.capacity,
            updated_at = EXCLUDED.updated_at
        """;

    private static final String UPDATE_CAPACITY = """
        UPDATE course_availability
        SET capacity   = ?,
            updated_at = ?
        WHERE course_id = ?
        """;

    private static final String INCREMENT_ENROLLED = """
        UPDATE course_availability
        SET enrolled   = enrolled + 1,
            updated_at = ?
        WHERE course_id = ?
        """;

    public CourseAvailabilityViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager,
            WriteDataSource writeDataSource) {
        super(objectMapper, clockProvider, transactionManager, writeDataSource);
    }

    @Override
    public String getViewName() {
        return "course-availability-view";
    }

    @Override
    protected Class<CourseEvent> getEventType() {
        return CourseEvent.class;
    }

    @Override
    protected boolean handleEvent(CourseEvent courseEvent, StoredEvent storedEvent, JdbcTemplate jdbcTemplate) {
        return switch (courseEvent) {
            case CourseDefined defined -> handleCourseDefined(defined, jdbcTemplate);
            case CourseCapacityChanged changed -> handleCapacityChanged(changed, jdbcTemplate);
            case StudentSubscribedToCourse subscribed -> handleStudentSubscribed(subscribed, jdbcTemplate);
        };
    }

    private boolean handleCourseDefined(CourseDefined defined, JdbcTemplate jdbc) {
        jdbc.update(UPSERT_COURSE,
            defined.courseId(),
            defined.capacity(),
            Timestamp.from(clockProvider.now())
        );
        return true;
    }

    private boolean handleCapacityChanged(CourseCapacityChanged changed, JdbcTemplate jdbc) {
        jdbc.update(UPDATE_CAPACITY,
            changed.newCapacity(),
            Timestamp.from(clockProvider.now()),
            changed.courseId()
        );
        return true;
    }

    private boolean handleStudentSubscribed(StudentSubscribedToCourse subscribed, JdbcTemplate jdbc) {
        jdbc.update(INCREMENT_ENROLLED,
            Timestamp.from(clockProvider.now()),
            subscribed.courseId()
        );
        return true;
    }
}
