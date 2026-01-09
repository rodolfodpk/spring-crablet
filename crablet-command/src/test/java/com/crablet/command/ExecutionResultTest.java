package com.crablet.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExecutionResult.
 * Tests factory methods and business logic methods used in CommandExecutorImpl.
 */
@DisplayName("ExecutionResult Unit Tests")
class ExecutionResultTest {

    @Test
    @DisplayName("created should create non-idempotent result")
    void created_ShouldCreateNonIdempotentResult() {
        // When
        ExecutionResult result = ExecutionResult.created();

        // Then
        assertThat(result.wasIdempotent()).isFalse();
        assertThat(result.wasCreated()).isTrue();
    }

    @Test
    @DisplayName("created should set reason to null")
    void created_ShouldSetReasonToNull() {
        // When
        ExecutionResult result = ExecutionResult.created();

        // Then
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("idempotent with reason should create idempotent result")
    void idempotent_WithReason_ShouldCreateIdempotentResult() {
        // Given
        String reason = "ALREADY_PROCESSED";

        // When
        ExecutionResult result = ExecutionResult.idempotent(reason);

        // Then
        assertThat(result.wasIdempotent()).isTrue();
        assertThat(result.wasCreated()).isFalse();
    }

    @Test
    @DisplayName("idempotent with reason should preserve reason")
    void idempotent_WithReason_ShouldPreserveReason() {
        // Given
        String reason = "DUPLICATE_OPERATION";

        // When
        ExecutionResult result = ExecutionResult.idempotent(reason);

        // Then
        assertThat(result.reason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("wasIdempotent when created should return false")
    void wasIdempotent_WhenCreated_ShouldReturnFalse() {
        // Given
        ExecutionResult result = ExecutionResult.created();

        // When
        boolean wasIdempotent = result.wasIdempotent();

        // Then
        assertThat(wasIdempotent).isFalse();
    }

    @Test
    @DisplayName("wasIdempotent when idempotent should return true")
    void wasIdempotent_WhenIdempotent_ShouldReturnTrue() {
        // Given
        ExecutionResult result = ExecutionResult.idempotent("ALREADY_PROCESSED");

        // When
        boolean wasIdempotent = result.wasIdempotent();

        // Then
        assertThat(wasIdempotent).isTrue();
    }

    @Test
    @DisplayName("wasCreated when created should return true")
    void wasCreated_WhenCreated_ShouldReturnTrue() {
        // Given
        ExecutionResult result = ExecutionResult.created();

        // When
        boolean wasCreated = result.wasCreated();

        // Then
        assertThat(wasCreated).isTrue();
    }

    @Test
    @DisplayName("wasCreated when idempotent should return false")
    void wasCreated_WhenIdempotent_ShouldReturnFalse() {
        // Given
        ExecutionResult result = ExecutionResult.idempotent("DUPLICATE_OPERATION");

        // When
        boolean wasCreated = result.wasCreated();

        // Then
        assertThat(wasCreated).isFalse();
    }
}

