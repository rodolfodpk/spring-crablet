package com.crablet.course.api;

import com.crablet.examples.course.exceptions.AlreadySubscribedException;
import com.crablet.examples.course.exceptions.CourseFullException;
import com.crablet.examples.course.exceptions.CourseNotFoundException;
import com.crablet.examples.course.exceptions.StudentSubscriptionLimitException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the course API.
 * Maps domain exceptions to RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CourseNotFoundException.class)
    public ProblemDetail handleCourseNotFound(CourseNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Course Not Found");
        problem.setProperty("courseId", ex.courseId);
        return problem;
    }

    @ExceptionHandler(CourseFullException.class)
    public ProblemDetail handleCourseFull(CourseFullException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Course Full");
        problem.setProperty("courseId", ex.courseId);
        problem.setProperty("enrolled", ex.currentSubscriptions);
        problem.setProperty("capacity", ex.capacity);
        return problem;
    }

    @ExceptionHandler(AlreadySubscribedException.class)
    public ProblemDetail handleAlreadySubscribed(AlreadySubscribedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Already Subscribed");
        problem.setProperty("studentId", ex.studentId);
        problem.setProperty("courseId", ex.courseId);
        return problem;
    }

    @ExceptionHandler(StudentSubscriptionLimitException.class)
    public ProblemDetail handleStudentLimit(StudentSubscriptionLimitException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Subscription Limit Reached");
        problem.setProperty("studentId", ex.studentId);
        problem.setProperty("currentSubscriptions", ex.currentSubscriptions);
        problem.setProperty("maxSubscriptions", ex.maxSubscriptions);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
