package com.crablet.command.web.internal;

import com.crablet.command.InvalidCommandException;
import com.crablet.eventstore.ConcurrencyException;
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
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request body"));
    }

    @ExceptionHandler(CommandNotExposedException.class)
    ResponseEntity<ProblemDetail> handleNotFound(CommandNotExposedException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler({InvalidCommandException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> handleInvalidCommand(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(ConcurrencyException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConcurrencyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected command API error"));
    }
}
