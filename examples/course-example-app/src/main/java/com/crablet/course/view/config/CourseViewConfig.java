package com.crablet.course.view.config;

import com.crablet.course.view.projectors.CourseAvailabilityViewProjector;
import com.crablet.examples.course.events.CourseCapacityChanged;
import com.crablet.examples.course.events.CourseDefined;
import com.crablet.examples.course.events.StudentSubscribedToCourse;
import com.crablet.views.ViewSubscription;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.crablet.eventstore.EventType.type;

@Configuration
public class CourseViewConfig {

    @Bean
    public ViewSubscription courseAvailabilityViewSubscription(CourseAvailabilityViewProjector projector) {
        return ViewSubscription.builder(projector.getViewName())
                .eventTypes(
                    type(CourseDefined.class),
                    type(CourseCapacityChanged.class),
                    type(StudentSubscribedToCourse.class)
                )
                .anyOfTags("course_id")
                .build();
    }
}
