package com.crablet.codegen.planning;

import com.crablet.codegen.model.CommandSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.EventSpec;
import com.crablet.codegen.model.ViewSpec;
import com.crablet.codegen.pipeline.SchemaResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseWorkflowFixtureTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final ArtifactPlanner planner = new ArtifactPlanner(new SchemaResolver());
    private final ModelValidator validator = new ModelValidator();

    @Test
    void coursePromptIsAConcreteWorkflowInput() throws Exception {
        String prompt = readResource("course/course-enrollment-prompt.md");

        assertThat(prompt).contains("I want to model course enrollment");
        assertThat(prompt).contains("a course must exist before a student can subscribe");
        assertThat(prompt).contains("a student must be registered before subscribing");
        assertThat(prompt).contains("a course cannot exceed its capacity");
        assertThat(prompt).contains("cannot subscribe to the same course twice");
        assertThat(prompt).contains("CourseAvailability");
    }

    @Test
    void courseCoreFixtureContainsTheMilestoneFacts() throws Exception {
        EventModel model = readCourseModel();

        assertThat(model.domain()).isEqualTo("CourseEnrollment");
        assertThat(model.basePackage()).isEqualTo("com.example.course");
        assertThat(model.events().stream().map(EventSpec::name))
                .containsExactly("CourseDefined", "StudentRegistered", "StudentSubscribedToCourse");
        assertThat(model.commands().stream().map(CommandSpec::name))
                .containsExactly("DefineCourse", "RegisterStudent", "SubscribeStudentToCourse");

        CommandSpec subscribe = model.commands().stream()
                .filter(command -> command.name().equals("SubscribeStudentToCourse"))
                .findFirst()
                .orElseThrow();
        assertThat(subscribe.isNonCommutative()).isTrue();
        assertThat(subscribe.guardEvents()).containsExactly("CourseDefined", "StudentRegistered");
        assertThat(subscribe.fields().stream().map(field -> field.name()))
                .containsExactly("courseId", "studentId");

        EventSpec subscribed = model.events().stream()
                .filter(event -> event.name().equals("StudentSubscribedToCourse"))
                .findFirst()
                .orElseThrow();
        assertThat(subscribed.tags()).containsExactly("course_id", "student_id");

        ViewSpec availability = model.views().stream()
                .filter(view -> view.name().equals("CourseAvailability"))
                .findFirst()
                .orElseThrow();
        assertThat(availability.reads()).containsExactly("CourseDefined", "StudentSubscribedToCourse");
        assertThat(availability.tag()).isEqualTo("course_id");
        assertThat(availability.fields().stream().map(field -> field.name()))
                .containsExactly("courseId", "title", "capacity", "enrolledCount", "seatsRemaining");

        assertThat(model.scenarios().stream().map(scenario -> scenario.name()))
                .containsExactly(
                        "Student subscribes successfully",
                        "Course is full",
                        "Student is already subscribed",
                        "Course does not exist",
                        "Student is not registered");
    }

    @Test
    void courseCoreFixturePlansExpectedArtifacts() throws Exception {
        String plan = planner.render(readCourseModel());

        assertThat(plan).contains("Planned artifacts for CourseEnrollment (com.example.course)");
        assertThat(plan).contains("com.example.course.domain.CourseEnrollmentEvent");
        assertThat(plan).contains("com.example.course.domain.CourseDefined");
        assertThat(plan).contains("com.example.course.domain.StudentRegistered");
        assertThat(plan).contains("com.example.course.domain.StudentSubscribedToCourse");
        assertThat(plan).contains("com.example.course.command.CourseEnrollmentState");
        assertThat(plan).contains("com.example.course.command.CourseEnrollmentStateProjector");
        assertThat(plan).contains("com.example.course.command.CourseEnrollmentQueryPatterns");
        assertThat(plan).contains("com.example.course.command.DefineCourse");
        assertThat(plan).contains("com.example.course.command.RegisterStudent");
        assertThat(plan).contains("com.example.course.command.SubscribeStudentToCourse");
        assertThat(plan).contains("com.example.course.view.CourseAvailabilityViewProjector");
        assertThat(plan).contains("V100__create_course_availability.sql");
        assertThat(plan).contains("com.example.course.test.StudentSubscribesSuccessfullyScenarioTest");
        assertThat(plan).contains("com.example.course.test.CourseIsFullScenarioTest");
        assertThat(plan).contains("com.example.course.test.StudentIsAlreadySubscribedScenarioTest");
        assertThat(plan).contains("com.example.course.test.CourseDoesNotExistScenarioTest");
        assertThat(plan).contains("com.example.course.test.StudentIsNotRegisteredScenarioTest");
    }

    @Test
    void planFailsFastWhenCourseModelIsMissingRequiredFacts() throws Exception {
        EventModel model = yaml.readValue("""
                domain: CourseEnrollment
                basePackage: com.example.course
                events:
                  - name: CourseDefined
                    tags: [course_id]
                    fields:
                      - name: courseId
                        type: string
                commands:
                  - name: SubscribeStudentToCourse
                    pattern: non-commutative
                    produces: [StudentSubscribedToCourse]
                    fields:
                      - name: courseId
                        type: string
                """, EventModel.class);

        assertThatThrownBy(() -> planner.render(model))
                .isInstanceOf(ModelValidationException.class)
                .hasMessageContaining("command 'SubscribeStudentToCourse' produces unknown event 'StudentSubscribedToCourse'");
    }

    @Test
    void validatorReportsUnsupportedViewShapeBeforeGeneration() throws Exception {
        EventModel model = yaml.readValue("""
                domain: CourseEnrollment
                basePackage: com.example.course
                events:
                  - name: CourseDefined
                    tags: [course_id]
                    fields:
                      - name: courseId
                        type: string
                commands:
                  - name: DefineCourse
                    pattern: idempotent
                    produces: [CourseDefined]
                    fields:
                      - name: courseId
                        type: string
                views:
                  - name: CourseAvailability
                    reads: [MissingEvent]
                    tag: student_id
                    fields:
                      - name: courseId
                        type: string
                """, EventModel.class);

        assertThatThrownBy(() -> validator.validate(model))
                .isInstanceOf(ModelValidationException.class)
                .hasMessageContaining("view 'CourseAvailability' reads unknown event 'MissingEvent'")
                .hasMessageContaining("view 'CourseAvailability' tag 'student_id' is not present on any read event");
    }

    private EventModel readCourseModel() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("course/course-core-event-model.yaml")) {
            assertThat(in).isNotNull();
            return yaml.readValue(in, EventModel.class);
        }
    }

    private String readResource(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
