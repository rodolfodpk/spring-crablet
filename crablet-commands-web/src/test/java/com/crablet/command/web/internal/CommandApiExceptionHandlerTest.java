package com.crablet.command.web.internal;

import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.DCBViolation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class CommandApiExceptionHandlerTest {

    private final CommandApiExceptionHandler handler = new CommandApiExceptionHandler();

    @Test
    void conflictProblemIncludesDcbViolationPropertiesWhenAvailable() {
        DCBViolation violation = new DCBViolation("GUARD_VIOLATION", "Concurrent lifecycle event detected", 1);
        ConcurrencyException exception = new ConcurrencyException("AppendCondition violated", violation);

        ResponseEntity<ProblemDetail> response = handler.handleConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = Objects.requireNonNull(response.getBody());
        Map<String, Object> properties = Objects.requireNonNull(body.getProperties());
        assertThat(body.getType()).isEqualTo(CommandApiProblemTypes.DCB_CONCURRENCY);
        assertThat(properties)
                .containsEntry("violationCode", "GUARD_VIOLATION")
                .containsEntry("matchingEventsCount", 1);
        assertThat(properties.get("hint")).asString().isNotBlank();
    }
}
