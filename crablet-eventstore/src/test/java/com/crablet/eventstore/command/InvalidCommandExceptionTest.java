package com.crablet.eventstore.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for InvalidCommandException.
 * Tests all constructors used in CommandExecutorImpl and CommandTypeResolver.
 */
@DisplayName("InvalidCommandException Unit Tests")
class InvalidCommandExceptionTest {

    @Test
    @DisplayName("constructor with message and command should set both fields")
    void constructor_WithMessageAndCommand_ShouldSetBothFields() {
        // Given
        String message = "Command validation failed";
        Object command = "test-command";

        // When
        InvalidCommandException exception = new InvalidCommandException(message, command);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isEqualTo(command);
        assertThat(exception.validationError).isEqualTo(message);
    }

    @Test
    @DisplayName("constructor with message and command should set validationError to message")
    void constructor_WithMessageAndCommand_ShouldSetValidationErrorToMessage() {
        // Given
        String message = "Invalid command type";
        Object command = "test-command";

        // When
        InvalidCommandException exception = new InvalidCommandException(message, command);

        // Then
        assertThat(exception.validationError).isEqualTo(message);
    }

    @Test
    @DisplayName("constructor with message and validationError should set error only")
    void constructor_WithMessageAndValidationError_ShouldSetErrorOnly() {
        // Given
        String message = "Command type extraction failed";
        String validationError = "NO_COMMAND_TYPE";

        // When
        InvalidCommandException exception = new InvalidCommandException(message, validationError);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.validationError).isEqualTo(validationError);
    }

    @Test
    @DisplayName("constructor with message and validationError should set command to null")
    void constructor_WithMessageAndValidationError_ShouldSetCommandToNull() {
        // Given
        String message = "Handler not found";
        String validationError = "UNKNOWN_HANDLER";

        // When
        InvalidCommandException exception = new InvalidCommandException(message, validationError);

        // Then
        assertThat(exception.command).isNull();
        assertThat(exception.validationError).isEqualTo(validationError);
    }

    @Test
    @DisplayName("constructor with message, command, and cause should set all fields")
    void constructor_WithMessageCommandAndCause_ShouldSetAllFields() {
        // Given
        String message = "Serialization failed";
        Object command = "test-command";
        Throwable cause = new RuntimeException("JSON parsing error");

        // When
        InvalidCommandException exception = new InvalidCommandException(message, command, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.command).isEqualTo(command);
        assertThat(exception.validationError).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("getCommand when set should return command")
    void getCommand_WhenSet_ShouldReturnCommand() {
        // Given
        Object command = "deposit-command-123";
        InvalidCommandException exception = new InvalidCommandException("Invalid command", command);

        // When
        Object retrievedCommand = exception.command;

        // Then
        assertThat(retrievedCommand).isEqualTo(command);
    }

    @Test
    @DisplayName("getCommand when not set should return null")
    void getCommand_WhenNotSet_ShouldReturnNull() {
        // Given
        InvalidCommandException exception = new InvalidCommandException("Error", "VALIDATION_ERROR");

        // When
        Object command = exception.command;

        // Then
        assertThat(command).isNull();
    }

    @Test
    @DisplayName("getValidationError should return validationError")
    void getValidationError_ShouldReturnValidationError() {
        // Given
        String validationError = "MISSING_REQUIRED_FIELD";
        InvalidCommandException exception = new InvalidCommandException("Validation failed", validationError);

        // When
        String retrievedError = exception.validationError;

        // Then
        assertThat(retrievedError).isEqualTo(validationError);
    }
}

