package com.crablet.command.web.internal;

import com.crablet.command.InvalidCommandException;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.DCBViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception mapping for the generic REST command API.
 * Scoped to {@link CommandApiRestController} so it does not interfere with the
 * application's own exception handlers.
 */
@RestControllerAdvice(assignableTypes = CommandApiRestController.class)
class CommandApiExceptionHandler {

    @ExceptionHandler(CommandApiBadRequestException.class)
    ResponseEntity<ProblemDetail> handleBadRequest(CommandApiBadRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setType(CommandApiProblemTypes.BAD_REQUEST);
        return ResponseEntity.badRequest()
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
        problem.setType(CommandApiProblemTypes.MALFORMED_JSON);
        return ResponseEntity.badRequest()
                .body(problem);
    }

    @ExceptionHandler(CommandNotExposedException.class)
    ResponseEntity<ProblemDetail> handleNotFound(CommandNotExposedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setType(CommandApiProblemTypes.COMMAND_NOT_EXPOSED);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem);
    }

    @ExceptionHandler({InvalidCommandException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> handleInvalidCommand(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setType(CommandApiProblemTypes.INVALID_COMMAND);
        return ResponseEntity.badRequest()
                .body(problem);
    }

    @ExceptionHandler(ConcurrencyException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConcurrencyException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setType(CommandApiProblemTypes.DCB_CONCURRENCY);
        DCBViolation violation = e.violation;
        if (violation != null) {
            if (violation.errorCode() != null) {
                problem.setProperty("violationCode", violation.errorCode());
            }
            problem.setProperty("matchingEventsCount", violation.matchingEventsCount());
            problem.setProperty("hint", "Refresh state and retry the command if it is still valid.");
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected command API error");
        problem.setType(CommandApiProblemTypes.UNEXPECTED_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem);
    }
}
