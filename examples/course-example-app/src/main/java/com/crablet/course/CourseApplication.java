package com.crablet.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Course Example Application.
 *
 * <p>Demonstrates multi-entity DCB constraints with course enrollment:
 * <ul>
 *   <li>Course capacity enforcement (course-scoped constraint)</li>
 *   <li>Student subscription limit (student-scoped constraint)</li>
 *   <li>Both constraints checked in a single decision model (the key DCB differentiator)</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
    "com.crablet.course",
    "com.crablet.examples.course.handlers"  // Required: scan handlers from shared-examples-domain
})
@EnableConfigurationProperties
public class CourseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
