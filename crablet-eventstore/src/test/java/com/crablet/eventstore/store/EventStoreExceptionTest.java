package com.crablet.eventstore.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EventStoreException.
 * Tests all constructors, exception chaining, and edge cases.
 */
@DisplayName("EventStoreException Unit Tests")
class EventStoreExceptionTest {

    @Test
    @DisplayName("Should create EventStoreException with message only")
    void shouldCreateEventStoreException_WithMessageOnly() {
        // Given
        String message = "Database connection failed";

        // When
        EventStoreException exception = new EventStoreException(message);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create EventStoreException with message and cause")
    void shouldCreateEventStoreException_WithMessageAndCause() {
        // Given
        String message = "Database connection failed";
        Throwable cause = new RuntimeException("Connection timeout");

        // When
        EventStoreException exception = new EventStoreException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should propagate message from constructor")
    void shouldPropagateMessage_FromConstructor() {
        // Given
        String expectedMessage = "Failed to append events";

        // When
        EventStoreException exception = new EventStoreException(expectedMessage);

        // Then
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("Should propagate cause when provided")
    void shouldPropagateCause_WhenProvided() {
        // Given
        String message = "Database operation failed";
        SQLException cause = new SQLException("Connection closed");

        // When
        EventStoreException exception = new EventStoreException(message, cause);

        // Then
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause()).isInstanceOf(SQLException.class);
        assertThat(exception.getCause().getMessage()).isEqualTo("Connection closed");
    }

    @Test
    @DisplayName("Should handle null message")
    void shouldHandleNullMessage_ShouldAllow() {
        // Given & When
        EventStoreException exception = new EventStoreException((String) null);

        // Then
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should handle null cause")
    void shouldHandleNullCause_ShouldAllow() {
        // Given
        String message = "Database operation failed";

        // When
        EventStoreException exception = new EventStoreException(message, (Throwable) null);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should be throwable and catchable")
    void shouldBeThrowableAndCatchable() {
        // Given
        String message = "Test exception";

        // When & Then
        assertThatThrownBy(() -> {
            throw new EventStoreException(message);
        })
                .isInstanceOf(EventStoreException.class)
                .isInstanceOf(RuntimeException.class)
                .hasMessage(message);
    }

    @Test
    @DisplayName("Should preserve exception chain")
    void shouldPreserveExceptionChain() {
        // Given
        String message = "Outer exception";
        RuntimeException rootCause = new RuntimeException("Root cause");
        IllegalArgumentException middleCause = new IllegalArgumentException("Middle cause", rootCause);

        // When
        EventStoreException exception = new EventStoreException(message, middleCause);

        // Then
        assertThat(exception.getCause()).isSameAs(middleCause);
        assertThat(exception.getCause().getCause()).isSameAs(rootCause);
    }

    @Test
    @DisplayName("Should handle empty message")
    void shouldHandleEmptyMessage() {
        // Given
        String emptyMessage = "";

        // When
        EventStoreException exception = new EventStoreException(emptyMessage);

        // Then
        assertThat(exception.getMessage()).isEmpty();
    }

    // Helper class for SQLException (not importing actual java.sql.SQLException for unit test)
    private static class SQLException extends RuntimeException {
        public SQLException(String message) {
            super(message);
        }
    }
}

