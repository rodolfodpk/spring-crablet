package com.crablet.course.api;

import com.crablet.examples.course.exceptions.CourseNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read-only query controller for course availability.
 * Queries the materialized view (not the event store) for fast reads.
 */
@RestController
@RequestMapping("/api/courses")
@Tag(name = "Course Queries", description = "Read-only queries from materialized views")
public class CourseQueryController {

    private final JdbcTemplate jdbcTemplate;

    public CourseQueryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Get course availability", description = "Returns capacity and enrollment count from the materialized view")
    public ResponseEntity<Map<String, Object>> getCourse(
            @Parameter(description = "Course identifier", required = true)
            @PathVariable String courseId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT course_id, capacity, enrolled, updated_at FROM course_availability WHERE course_id = ?",
            courseId
        );

        if (rows.isEmpty()) {
            throw new CourseNotFoundException(courseId);
        }

        Map<String, Object> row = rows.get(0);
        int capacity = (int) row.get("capacity");
        int enrolled = (int) row.get("enrolled");

        return ResponseEntity.ok(Map.of(
            "courseId",   row.get("course_id"),
            "capacity",   capacity,
            "enrolled",   enrolled,
            "available",  capacity - enrolled,
            "full",       enrolled >= capacity,
            "updatedAt",  row.get("updated_at")
        ));
    }
}
