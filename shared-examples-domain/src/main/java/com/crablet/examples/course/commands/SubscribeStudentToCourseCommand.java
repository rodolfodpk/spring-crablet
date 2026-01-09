package com.crablet.examples.course.commands;

import am.ik.yavi.arguments.Arguments2Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.examples.course.CourseCommand;

/**
 * Command to subscribe a student to a course.
 */
public record SubscribeStudentToCourseCommand(
        String studentId,
        String courseId
) implements CourseCommand {

    public SubscribeStudentToCourseCommand {
        try {
            validator.lazy().validated(studentId, courseId);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid SubscribeStudentToCourseCommand: " + e.getMessage(), e);
        }
    }

    private static Arguments2Validator<String, String, SubscribeStudentToCourseCommand> validator =
            Yavi.arguments()
                    ._string("studentId", c -> c.notNull().notBlank())
                    ._string("courseId", c -> c.notNull().notBlank())
                    .apply(SubscribeStudentToCourseCommand::new);

    public static SubscribeStudentToCourseCommand of(String studentId, String courseId) {
        return new SubscribeStudentToCourseCommand(studentId, courseId);
    }
}

