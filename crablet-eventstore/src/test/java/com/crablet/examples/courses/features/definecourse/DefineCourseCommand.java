package com.crablet.examples.courses.features.definecourse;

import am.ik.yavi.arguments.Arguments2Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.examples.courses.domain.CourseCommand;

/**
 * Command to define a new course.
 */
public record DefineCourseCommand(
        String courseId,
        int capacity
) implements CourseCommand {

    public DefineCourseCommand {
        try {
            validator.lazy().validated(courseId, capacity);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid DefineCourseCommand: " + e.getMessage(), e);
        }
    }

    private static Arguments2Validator<String, Integer, DefineCourseCommand> validator =
            Yavi.arguments()
                    ._string("courseId", c -> c.notNull().notBlank())
                    ._integer("capacity", c -> c.greaterThan(0))
                    .apply(DefineCourseCommand::new);

    public static DefineCourseCommand of(String courseId, int capacity) {
        return new DefineCourseCommand(courseId, capacity);
    }

    @Override
    public String getCommandType() {
        return "define_course";
    }
}

