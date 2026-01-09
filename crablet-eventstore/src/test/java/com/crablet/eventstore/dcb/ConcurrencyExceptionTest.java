package com.crablet.eventstore.dcb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ConcurrencyException.
 * Tests all constructor variations, field initialization, and exception behavior.
 */
@DisplayName("ConcurrencyException Unit Tests")
class ConcurrencyExceptionTest {

    @Test
    @DisplayName("Should create ConcurrencyException with message only")
    void shouldCreateConcurrencyException_WithMessageOnly() {
        // Given
        String message = "Concurrency violation detected";

        // When
        ConcurrencyException exception = new ConcurrencyException(message);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isNull();
        assertThat(exception.violation).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create ConcurrencyException with message and command")
    void shouldCreateConcurrencyException_WithMessageAndCommand() {
        // Given
        String message = "Concurrency violation detected";
        Object command = new TestCommand("wallet-123");

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isSameAs(command);
        assertThat(exception.violation).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create ConcurrencyException with message, command, and cause")
    void shouldCreateConcurrencyException_WithMessageCommandAndCause() {
        // Given
        String message = "Concurrency violation detected";
        Object command = new TestCommand("wallet-123");
        Throwable cause = new IllegalStateException("Underlying cause");

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isSameAs(command);
        assertThat(exception.violation).isNull();
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Should create ConcurrencyException with message, command, and violation")
    void shouldCreateConcurrencyException_WithMessageCommandAndViolation() {
        // Given
        String message = "Concurrency violation detected";
        Object command = new TestCommand("wallet-123");
        DCBViolation violation = new DCBViolation("DCB_VIOLATION", "Cursor mismatch", 2);

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command, violation);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isSameAs(command);
        assertThat(exception.violation).isSameAs(violation);
        assertThat(exception.violation.errorCode()).isEqualTo("DCB_VIOLATION");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create ConcurrencyException with message and violation")
    void shouldCreateConcurrencyException_WithMessageAndViolation() {
        // Given
        String message = "Concurrency violation detected";
        DCBViolation violation = new DCBViolation("DCB_VIOLATION", "Cursor mismatch", 2);

        // When
        ConcurrencyException exception = new ConcurrencyException(message, violation);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isNull();
        assertThat(exception.violation).isSameAs(violation);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should initialize fields when command provided")
    void shouldInitializeFields_WhenCommandProvided() {
        // Given
        String message = "Test message";
        Object command = new TestCommand("test-id");

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command);

        // Then
        assertThat(exception.command).isNotNull();
        assertThat(exception.command).isSameAs(command);
        assertThat(((TestCommand) exception.command).id()).isEqualTo("test-id");
    }

    @Test
    @DisplayName("Should initialize fields when violation provided")
    void shouldInitializeFields_WhenViolationProvided() {
        // Given
        String message = "Test message";
        DCBViolation violation = new DCBViolation("ERROR_CODE", "Error message", 5);

        // When
        ConcurrencyException exception = new ConcurrencyException(message, violation);

        // Then
        assertThat(exception.violation).isNotNull();
        assertThat(exception.violation).isSameAs(violation);
        assertThat(exception.violation.errorCode()).isEqualTo("ERROR_CODE");
        assertThat(exception.violation.message()).isEqualTo("Error message");
        assertThat(exception.violation.matchingEventsCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should propagate message from constructor")
    void shouldPropagateMessage_FromConstructor() {
        // Given
        String expectedMessage = "Custom error message";

        // When
        ConcurrencyException exception1 = new ConcurrencyException(expectedMessage);
        ConcurrencyException exception2 = new ConcurrencyException(expectedMessage, new TestCommand("id"));
        ConcurrencyException exception3 = new ConcurrencyException(expectedMessage, new DCBViolation("ERR", "msg", 1));

        // Then
        assertThat(exception1.getMessage()).isEqualTo(expectedMessage);
        assertThat(exception2.getMessage()).isEqualTo(expectedMessage);
        assertThat(exception3.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("Should propagate cause when provided")
    void shouldPropagateCause_WhenProvided() {
        // Given
        String message = "Test message";
        Object command = new TestCommand("id");
        IllegalArgumentException cause = new IllegalArgumentException("Root cause");

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command, cause);

        // Then
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exception.getCause().getMessage()).isEqualTo("Root cause");
    }

    @Test
    @DisplayName("Should handle null command")
    void shouldHandleNullCommand_ShouldAllow() {
        // Given
        String message = "Test message";

        // When
        ConcurrencyException exception = new ConcurrencyException(message, (Object) null);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isNull();
        assertThat(exception.violation).isNull();
    }

    @Test
    @DisplayName("Should handle null violation")
    void shouldHandleNullViolation_ShouldAllow() {
        // Given
        String message = "Test message";
        Object command = new TestCommand("id");

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command, (DCBViolation) null);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isSameAs(command);
        assertThat(exception.violation).isNull();
    }

    @Test
    @DisplayName("Should handle null message")
    void shouldHandleNullMessage_ShouldAllow() {
        // Given & When
        ConcurrencyException exception = new ConcurrencyException((String) null);

        // Then
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.command).isNull();
        assertThat(exception.violation).isNull();
    }

    @Test
    @DisplayName("Should handle null message with command")
    void shouldHandleNullMessage_WithCommand() {
        // Given
        Object command = new TestCommand("id");

        // When
        ConcurrencyException exception = new ConcurrencyException(null, command);

        // Then
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.command).isSameAs(command);
    }

    @Test
    @DisplayName("Should handle null message with violation")
    void shouldHandleNullMessage_WithViolation() {
        // Given
        DCBViolation violation = new DCBViolation("ERR", "msg", 1);

        // When
        ConcurrencyException exception = new ConcurrencyException(null, violation);

        // Then
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.violation).isSameAs(violation);
    }

    @Test
    @DisplayName("Should handle null cause")
    void shouldHandleNullCause_ShouldAllow() {
        // Given
        String message = "Test message";
        Object command = new TestCommand("id");

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command, (Throwable) null);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isSameAs(command);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should implement toString with command and violation")
    void shouldImplementToString_WithCommandAndViolation() {
        // Given
        String message = "Test message";
        Object command = new TestCommand("wallet-123");
        DCBViolation violation = new DCBViolation("DCB_VIOLATION", "Cursor mismatch", 2);

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command, violation);

        // Then - RuntimeException's toString includes message
        String toString = exception.toString();
        assertThat(toString)
                .contains("ConcurrencyException")
                .contains(message);
        // Note: command and violation are not automatically included in toString(),
        // but they are accessible via public fields
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    void shouldBeThrowableAndCatchable() {
        // Given
        String message = "Test exception";

        // When & Then
        assertThatThrownBy(() -> {
            throw new ConcurrencyException(message);
        })
                .isInstanceOf(ConcurrencyException.class)
                .isInstanceOf(RuntimeException.class)
                .hasMessage(message);
    }

    @Test
    @DisplayName("Should preserve exception chain")
    void shouldPreserveExceptionChain() {
        // Given
        String message = "Outer exception";
        RuntimeException rootCause = new RuntimeException("Root cause");
        Object command = new TestCommand("id");

        // When
        ConcurrencyException exception = new ConcurrencyException(message, command, rootCause);

        // Then
        assertThat(exception.getCause()).isSameAs(rootCause);
        assertThat(exception.getCause().getCause()).isNull(); // No nested chain
    }

    @Test
    @DisplayName("Should allow all fields to be null")
    void shouldAllowAllFieldsToBeNull() {
        // Given & When
        ConcurrencyException exception = new ConcurrencyException(null);

        // Then
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.command).isNull();
        assertThat(exception.violation).isNull();
        assertThat(exception.getCause()).isNull();
    }

    // Helper class for testing
    private record TestCommand(String id) {}
}

