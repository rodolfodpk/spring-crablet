package com.crablet.examples.course.commands;

import com.crablet.examples.course.CourseCommand;

/**
 * Command to register a student before course subscription.
 */
public record RegisterStudentCommand(
        String studentId
) implements CourseCommand {

    public RegisterStudentCommand {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid RegisterStudentCommand: studentId must not be blank");
        }
    }

    public static RegisterStudentCommand of(String studentId) {
        return new RegisterStudentCommand(studentId);
    }
}
