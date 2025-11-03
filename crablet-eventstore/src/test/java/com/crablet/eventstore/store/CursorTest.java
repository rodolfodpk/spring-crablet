package com.crablet.eventstore.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Cursor value object.
 * Tests all factory methods, equality/hashCode, and edge cases.
 */
@DisplayName("Cursor Unit Tests")
class CursorTest {

    @Test
    @DisplayName("Should create cursor with position, timestamp, and transaction ID")
    void shouldCreateCursor_WithPositionTimestampAndTransactionId() {
        // Given
        SequenceNumber position = SequenceNumber.of(100L);
        Instant occurredAt = Instant.now();
        String transactionId = "tx-123";

        // When
        Cursor cursor = Cursor.of(position, occurredAt, transactionId);

        // Then
        assertThat(cursor.position()).isEqualTo(position);
        assertThat(cursor.occurredAt()).isEqualTo(occurredAt);
        assertThat(cursor.transactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Should create cursor with position and timestamp (default transaction ID)")
    void shouldCreateCursor_WithPositionAndTimestamp() {
        // Given
        SequenceNumber position = SequenceNumber.of(100L);
        Instant occurredAt = Instant.now();

        // When
        Cursor cursor = Cursor.of(position, occurredAt);

        // Then
        assertThat(cursor.position()).isEqualTo(position);
        assertThat(cursor.occurredAt()).isEqualTo(occurredAt);
        assertThat(cursor.transactionId()).isEqualTo("0");
    }

    @Test
    @DisplayName("Should create cursor with position only (default timestamp and transaction ID)")
    void shouldCreateCursor_WithPositionOnly() {
        // Given
        SequenceNumber position = SequenceNumber.of(100L);
        Instant before = Instant.now();

        // When
        Cursor cursor = Cursor.of(position);
        Instant after = Instant.now();

        // Then
        assertThat(cursor.position()).isEqualTo(position);
        assertThat(cursor.transactionId()).isEqualTo("0");
        assertThat(cursor.occurredAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should create cursor with long position (convenience method)")
    void shouldCreateCursor_WithLongPosition() {
        // Given
        long position = 100L;
        Instant before = Instant.now();

        // When
        Cursor cursor = Cursor.of(position);
        Instant after = Instant.now();

        // Then
        assertThat(cursor.position().value()).isEqualTo(position);
        assertThat(cursor.transactionId()).isEqualTo("0");
        assertThat(cursor.occurredAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should create cursor with long position and timestamp (convenience method)")
    void shouldCreateCursor_WithLongPositionAndTimestamp() {
        // Given
        long position = 100L;
        Instant occurredAt = Instant.now();

        // When
        Cursor cursor = Cursor.of(position, occurredAt);

        // Then
        assertThat(cursor.position().value()).isEqualTo(position);
        assertThat(cursor.occurredAt()).isEqualTo(occurredAt);
        assertThat(cursor.transactionId()).isEqualTo("0");
    }

    @Test
    @DisplayName("Should create cursor with long position, timestamp, and transaction ID (convenience method)")
    void shouldCreateCursor_WithLongPositionTimestampAndTransactionId() {
        // Given
        long position = 100L;
        Instant occurredAt = Instant.now();
        String transactionId = "tx-456";

        // When
        Cursor cursor = Cursor.of(position, occurredAt, transactionId);

        // Then
        assertThat(cursor.position().value()).isEqualTo(position);
        assertThat(cursor.occurredAt()).isEqualTo(occurredAt);
        assertThat(cursor.transactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Should create zero cursor")
    void shouldCreateZeroCursor() {
        // When
        Cursor cursor = Cursor.zero();

        // Then
        assertThat(cursor.position()).isEqualTo(SequenceNumber.zero());
        assertThat(cursor.position().value()).isEqualTo(0);
        assertThat(cursor.occurredAt()).isEqualTo(Instant.EPOCH);
        assertThat(cursor.transactionId()).isEqualTo("0");
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        SequenceNumber position = SequenceNumber.of(100L);
        Instant occurredAt = Instant.now();
        String transactionId = "tx-123";

        // When
        Cursor cursor1 = Cursor.of(position, occurredAt, transactionId);
        Cursor cursor2 = Cursor.of(position, occurredAt, transactionId);

        // Then
        assertThat(cursor1).isEqualTo(cursor2);
        assertThat(cursor1.hashCode()).isEqualTo(cursor2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different positions")
    void shouldImplementEquals_WithDifferentPositions() {
        // Given
        Instant occurredAt = Instant.now();
        String transactionId = "tx-123";

        // When
        Cursor cursor1 = Cursor.of(SequenceNumber.of(100L), occurredAt, transactionId);
        Cursor cursor2 = Cursor.of(SequenceNumber.of(200L), occurredAt, transactionId);

        // Then
        assertThat(cursor1).isNotEqualTo(cursor2);
    }

    @Test
    @DisplayName("Should implement equals with different transaction IDs")
    void shouldImplementEquals_WithDifferentTransactionIds() {
        // Given
        SequenceNumber position = SequenceNumber.of(100L);
        Instant occurredAt = Instant.now();

        // When
        Cursor cursor1 = Cursor.of(position, occurredAt, "tx-123");
        Cursor cursor2 = Cursor.of(position, occurredAt, "tx-456");

        // Then
        assertThat(cursor1).isNotEqualTo(cursor2);
    }

    @Test
    @DisplayName("Should handle zero position")
    void shouldHandleZeroPosition() {
        // Given
        SequenceNumber zeroPosition = SequenceNumber.zero();
        Instant occurredAt = Instant.now();

        // When
        Cursor cursor = Cursor.of(zeroPosition, occurredAt, "tx-123");

        // Then
        assertThat(cursor.position().value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle max position")
    void shouldHandleMaxPosition() {
        // Given
        SequenceNumber maxPosition = SequenceNumber.of(Long.MAX_VALUE);
        Instant occurredAt = Instant.now();

        // When
        Cursor cursor = Cursor.of(maxPosition, occurredAt, "tx-123");

        // Then
        assertThat(cursor.position().value()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle null transaction ID")
    void shouldHandleNullTransactionId_ShouldAllow() {
        // Given - Records allow null fields unless validated
        SequenceNumber position = SequenceNumber.of(100L);
        Instant occurredAt = Instant.now();

        // When - Direct constructor allows null
        Cursor cursor = new Cursor(position, occurredAt, null);

        // Then
        assertThat(cursor.transactionId()).isNull();
    }

    @Test
    @DisplayName("Should throw exception when long position is negative")
    void shouldThrowException_WhenLongPositionIsNegative() {
        // Given
        long negativePosition = -1L;

        // When & Then - SequenceNumber.of() validates negative values
        assertThatThrownBy(() -> Cursor.of(negativePosition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Should handle null occurredAt")
    void shouldHandleNullOccurredAt_ShouldAllow() {
        // Given - Records allow null fields unless validated
        SequenceNumber position = SequenceNumber.of(100L);

        // When - Direct constructor allows null
        Cursor cursor = new Cursor(position, null, "tx-123");

        // Then
        assertThat(cursor.occurredAt()).isNull();
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToString_Correctly() {
        // Given
        SequenceNumber position = SequenceNumber.of(100L);
        Instant occurredAt = Instant.now();
        String transactionId = "tx-123";

        // When
        Cursor cursor = Cursor.of(position, occurredAt, transactionId);

        // Then - Record auto-generates toString()
        String toString = cursor.toString();
        assertThat(toString)
                .contains("Cursor[")
                .contains("position=")
                .contains("occurredAt=")
                .contains("transactionId=");
    }
}

