package com.crablet.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PublishException.
 * Tests constructors, exception chaining, and edge cases.
 */
@DisplayName("PublishException Unit Tests")
class PublishExceptionTest {

    @Test
    @DisplayName("Should create PublishException with message and cause")
    void shouldCreatePublishException_WithMessageAndCause() {
        // Given
        String message = "Failed to publish events";
        Throwable cause = new RuntimeException("Network timeout");

        // When
        PublishException exception = new PublishException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should propagate message from constructor")
    void shouldPropagateMessage_FromConstructor() {
        // Given
        String expectedMessage = "Publishing failed due to connection error";
        Throwable cause = new RuntimeException("Connection closed");

        // When
        PublishException exception = new PublishException(expectedMessage, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("Should propagate cause when provided")
    void shouldPropagateCause_WhenProvided() {
        // Given
        String message = "Publishing operation failed";
        IllegalArgumentException cause = new IllegalArgumentException("Invalid event data");

        // When
        PublishException exception = new PublishException(message, cause);

        // Then
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exception.getCause().getMessage()).isEqualTo("Invalid event data");
    }

    @Test
    @DisplayName("Should preserve exception chain")
    void shouldPreserveExceptionChain() {
        // Given
        String message = "Outer exception";
        RuntimeException rootCause = new RuntimeException("Root cause");
        IllegalStateException middleCause = new IllegalStateException("Middle cause", rootCause);

        // When
        PublishException exception = new PublishException(message, middleCause);

        // Then
        assertThat(exception.getCause()).isSameAs(middleCause);
        assertThat(exception.getCause().getCause()).isSameAs(rootCause);
    }

    @Test
    @DisplayName("Should handle null message")
    void shouldHandleNullMessage_ShouldAllow() {
        // Given
        Throwable cause = new RuntimeException("Error");

        // When
        PublishException exception = new PublishException(null, cause);

        // Then
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Should handle null cause")
    void shouldHandleNullCause_ShouldAllow() {
        // Given
        String message = "Publishing failed";

        // When
        PublishException exception = new PublishException(message, null);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should handle empty message")
    void shouldHandleEmptyMessage() {
        // Given
        String emptyMessage = "";
        Throwable cause = new RuntimeException("Error");

        // When
        PublishException exception = new PublishException(emptyMessage, cause);

        // Then
        assertThat(exception.getMessage()).isEmpty();
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    void shouldBeThrowableAndCatchable() {
        // Given
        String message = "Test exception";
        Throwable cause = new RuntimeException("Test cause");

        // When & Then
        assertThatThrownBy(() -> {
            throw new PublishException(message, cause);
        })
                .isInstanceOf(PublishException.class)
                .isInstanceOf(Exception.class)
                .hasMessage(message)
                .hasCause(cause);
    }

    @Test
    @DisplayName("Should handle null message and null cause")
    void shouldHandleNullMessageAndNullCause_ShouldAllow() {
        // Given & When
        PublishException exception = new PublishException(null, null);

        // Then
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should be checkable exception (extends Exception, not RuntimeException)")
    void shouldBeCheckedException() {
        // Given
        PublishException exception = new PublishException("Error", new RuntimeException());

        // When & Then - Checked exception (extends Exception, not RuntimeException)
        assertThat(exception).isInstanceOf(Exception.class);
        assertThat(exception).isNotInstanceOf(RuntimeException.class);
    }
}

