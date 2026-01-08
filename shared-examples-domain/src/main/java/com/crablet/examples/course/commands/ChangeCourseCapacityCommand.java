package com.crablet.examples.course.commands;

import am.ik.yavi.arguments.Arguments2Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;
import com.crablet.examples.course.CourseCommand;

/**
 * Command to change a course's capacity.
 */
public record ChangeCourseCapacityCommand(
        String courseId,
        int newCapacity
) implements CourseCommand {

    public ChangeCourseCapacityCommand {
        try {
            validator.lazy().validated(courseId, newCapacity);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid ChangeCourseCapacityCommand: " + e.getMessage(), e);
        }
    }

    private static Arguments2Validator<String, Integer, ChangeCourseCapacityCommand> validator =
            Yavi.arguments()
                    ._string("courseId", c -> c.notNull().notBlank())
                    ._integer("newCapacity", c -> c.greaterThan(0))
                    .apply(ChangeCourseCapacityCommand::new);

    public static ChangeCourseCapacityCommand of(String courseId, int newCapacity) {
        return new ChangeCourseCapacityCommand(courseId, newCapacity);
    }
}

