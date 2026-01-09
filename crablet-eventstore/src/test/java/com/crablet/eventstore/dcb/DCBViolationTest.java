package com.crablet.eventstore.dcb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DCBViolation value object.
 * Tests constructor, toString(), equals/hashCode, and edge cases.
 */
@DisplayName("DCBViolation Unit Tests")
class DCBViolationTest {

    @Test
    @DisplayName("Should create DCBViolation with all parameters")
    void shouldCreateDCBViolation_WithAllParameters() {
        // Given
        String errorCode = "DCB_VIOLATION";
        String message = "Append condition violated: cursor mismatch";
        int matchingEventsCount = 2;

        // When
        DCBViolation violation = new DCBViolation(errorCode, message, matchingEventsCount);

        // Then
        assertThat(violation.errorCode()).isEqualTo(errorCode);
        assertThat(violation.message()).isEqualTo(message);
        assertThat(violation.matchingEventsCount()).isEqualTo(matchingEventsCount);
    }

    @Test
    @DisplayName("Should implement toString with formatted message")
    void shouldImplementToString_WithFormattedMessage() {
        // Given
        String errorCode = "DCB_VIOLATION";
        String message = "Append condition violated";
        int matchingEventsCount = 5;

        // When
        DCBViolation violation = new DCBViolation(errorCode, message, matchingEventsCount);

        // Then
        String toString = violation.toString();
        assertThat(toString)
                .contains("DCBViolation{")
                .contains("errorCode='" + errorCode + "'")
                .contains("message='" + message + "'")
                .contains("matchingEvents=" + matchingEventsCount)
                .endsWith("}");
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        String errorCode = "DCB_VIOLATION";
        String message = "Append condition violated";
        int matchingEventsCount = 3;

        // When
        DCBViolation violation1 = new DCBViolation(errorCode, message, matchingEventsCount);
        DCBViolation violation2 = new DCBViolation(errorCode, message, matchingEventsCount);

        // Then
        assertThat(violation1).isEqualTo(violation2);
        assertThat(violation1.hashCode()).isEqualTo(violation2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different errorCode")
    void shouldImplementEquals_WithDifferentErrorCode() {
        // Given
        String message = "Append condition violated";
        int matchingEventsCount = 3;

        // When
        DCBViolation violation1 = new DCBViolation("DCB_VIOLATION", message, matchingEventsCount);
        DCBViolation violation2 = new DCBViolation("CURSOR_MISMATCH", message, matchingEventsCount);

        // Then
        assertThat(violation1).isNotEqualTo(violation2);
    }

    @Test
    @DisplayName("Should implement equals with different message")
    void shouldImplementEquals_WithDifferentMessage() {
        // Given
        String errorCode = "DCB_VIOLATION";
        int matchingEventsCount = 3;

        // When
        DCBViolation violation1 = new DCBViolation(errorCode, "Message 1", matchingEventsCount);
        DCBViolation violation2 = new DCBViolation(errorCode, "Message 2", matchingEventsCount);

        // Then
        assertThat(violation1).isNotEqualTo(violation2);
    }

    @Test
    @DisplayName("Should implement equals with different matchingEventsCount")
    void shouldImplementEquals_WithDifferentMatchingEventsCount() {
        // Given
        String errorCode = "DCB_VIOLATION";
        String message = "Append condition violated";

        // When
        DCBViolation violation1 = new DCBViolation(errorCode, message, 2);
        DCBViolation violation2 = new DCBViolation(errorCode, message, 5);

        // Then
        assertThat(violation1).isNotEqualTo(violation2);
    }

    @Test
    @DisplayName("Should handle null errorCode")
    void shouldHandleNullErrorCode_ShouldAllow() {
        // Given
        String message = "Append condition violated";
        int matchingEventsCount = 1;

        // When
        DCBViolation violation = new DCBViolation(null, message, matchingEventsCount);

        // Then
        assertThat(violation.errorCode()).isNull();
        assertThat(violation.message()).isEqualTo(message);
        assertThat(violation.matchingEventsCount()).isEqualTo(matchingEventsCount);
    }

    @Test
    @DisplayName("Should handle null message")
    void shouldHandleNullMessage_ShouldAllow() {
        // Given
        String errorCode = "DCB_VIOLATION";
        int matchingEventsCount = 1;

        // When
        DCBViolation violation = new DCBViolation(errorCode, null, matchingEventsCount);

        // Then
        assertThat(violation.errorCode()).isEqualTo(errorCode);
        assertThat(violation.message()).isNull();
        assertThat(violation.matchingEventsCount()).isEqualTo(matchingEventsCount);
    }

    @Test
    @DisplayName("Should handle null errorCode and null message")
    void shouldHandleNullErrorCodeAndNullMessage_ShouldAllow() {
        // Given
        int matchingEventsCount = 1;

        // When
        DCBViolation violation = new DCBViolation(null, null, matchingEventsCount);

        // Then
        assertThat(violation.errorCode()).isNull();
        assertThat(violation.message()).isNull();
        assertThat(violation.matchingEventsCount()).isEqualTo(matchingEventsCount);
    }

    @Test
    @DisplayName("Should handle zero matchingEventsCount")
    void shouldHandleZeroMatchingEventsCount() {
        // Given
        String errorCode = "DCB_VIOLATION";
        String message = "Append condition violated";

        // When
        DCBViolation violation = new DCBViolation(errorCode, message, 0);

        // Then
        assertThat(violation.matchingEventsCount()).isEqualTo(0);
        assertThat(violation.toString()).contains("matchingEvents=0");
    }

    @Test
    @DisplayName("Should handle negative matchingEventsCount")
    void shouldHandleNegativeMatchingEventsCount() {
        // Given
        String errorCode = "DCB_VIOLATION";
        String message = "Append condition violated";

        // When
        DCBViolation violation = new DCBViolation(errorCode, message, -1);

        // Then
        assertThat(violation.matchingEventsCount()).isEqualTo(-1);
        assertThat(violation.toString()).contains("matchingEvents=-1");
    }

    @Test
    @DisplayName("Should handle empty errorCode")
    void shouldHandleEmptyErrorCode() {
        // Given
        String message = "Append condition violated";
        int matchingEventsCount = 1;

        // When
        DCBViolation violation = new DCBViolation("", message, matchingEventsCount);

        // Then
        assertThat(violation.errorCode()).isEmpty();
        assertThat(violation.message()).isEqualTo(message);
    }

    @Test
    @DisplayName("Should handle empty message")
    void shouldHandleEmptyMessage() {
        // Given
        String errorCode = "DCB_VIOLATION";
        int matchingEventsCount = 1;

        // When
        DCBViolation violation = new DCBViolation(errorCode, "", matchingEventsCount);

        // Then
        assertThat(violation.errorCode()).isEqualTo(errorCode);
        assertThat(violation.message()).isEmpty();
    }

    @Test
    @DisplayName("Should handle large matchingEventsCount")
    void shouldHandleLargeMatchingEventsCount() {
        // Given
        String errorCode = "DCB_VIOLATION";
        String message = "Append condition violated";
        int largeCount = Integer.MAX_VALUE;

        // When
        DCBViolation violation = new DCBViolation(errorCode, message, largeCount);

        // Then
        assertThat(violation.matchingEventsCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle toString with null values")
    void shouldHandleToString_WithNullValues() {
        // Given
        DCBViolation violation = new DCBViolation(null, null, 0);

        // When
        String toString = violation.toString();

        // Then
        assertThat(toString)
                .contains("DCBViolation{")
                .contains("errorCode='null'")  // String.format converts null to "null"
                .contains("message='null'")
                .contains("matchingEvents=0");
    }
}

