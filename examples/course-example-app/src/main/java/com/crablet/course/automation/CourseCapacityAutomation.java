package com.crablet.course.automation;

import com.crablet.automations.AutomationDecision;
import com.crablet.automations.AutomationHandler;
import com.crablet.eventstore.EventType;
import com.crablet.eventstore.StoredEvent;
import com.crablet.examples.course.events.StudentSubscribedToCourse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Automation that monitors course capacity after each enrollment.
 * Logs a warning when a course reaches full capacity.
 * Demonstrates the automation pattern: listen to events, check derived state, act (or not).
 */
@Component
public class CourseCapacityAutomation implements AutomationHandler {

    private static final Logger log = LoggerFactory.getLogger(CourseCapacityAutomation.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public CourseCapacityAutomation(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getAutomationName() {
        return "course-capacity-monitor";
    }

    @Override
    public Set<String> getEventTypes() {
        return Set.of(EventType.type(StudentSubscribedToCourse.class));
    }

    @Override
    public List<AutomationDecision> decide(StoredEvent event) {
        try {
            StudentSubscribedToCourse subscribed = objectMapper.readValue(event.data(), StudentSubscribedToCourse.class);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT capacity, enrolled FROM course_availability WHERE course_id = ?",
                subscribed.courseId()
            );

            if (!rows.isEmpty()) {
                int capacity = (int) rows.get(0).get("capacity");
                int enrolled = (int) rows.get(0).get("enrolled");
                if (enrolled >= capacity) {
                    log.warn("Course {} is now full: enrolled={}, capacity={}", subscribed.courseId(), enrolled, capacity);
                } else {
                    log.info("Student {} enrolled in course {}: enrolled={}/{}",
                        subscribed.studentId(), subscribed.courseId(), enrolled, capacity);
                }
            }

            return List.of(AutomationDecision.NoOp.empty());
        } catch (Exception e) {
            log.error("CourseCapacityAutomation failed for event position={}: {}", event.position(), e.getMessage(), e);
            throw new RuntimeException("CourseCapacityAutomation failed", e);
        }
    }
}
