package com.crablet.eventstore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StreamPosition value object.
 */
@DisplayName("StreamPosition Unit Tests")
class StreamPositionTest {

    @Test
    @DisplayName("Should create stream position with position, timestamp, and transaction ID")
    void shouldCreateStreamPosition_WithPositionTimestampAndTransactionId() {
        // Given
        long position = 100L;
        Instant occurredAt = Instant.now();
        String transactionId = "tx-123";

        // When
        StreamPosition streamPosition = StreamPosition.of(position, occurredAt, transactionId);

        // Then
        assertThat(streamPosition.position()).isEqualTo(position);
        assertThat(streamPosition.occurredAt()).isEqualTo(occurredAt);
        assertThat(streamPosition.transactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Should create stream position with position and timestamp (default transaction ID)")
    void shouldCreateStreamPosition_WithPositionAndTimestamp() {
        // Given
        long position = 100L;
        Instant occurredAt = Instant.now();

        // When
        StreamPosition streamPosition = StreamPosition.of(position, occurredAt);

        // Then
        assertThat(streamPosition.position()).isEqualTo(position);
        assertThat(streamPosition.occurredAt()).isEqualTo(occurredAt);
        assertThat(streamPosition.transactionId()).isEqualTo("0");
    }

    @Test
    @DisplayName("Should create stream position with position only (default timestamp and transaction ID)")
    void shouldCreateStreamPosition_WithPositionOnly() {
        // Given
        long position = 100L;
        Instant before = Instant.now();

        // When
        StreamPosition streamPosition = StreamPosition.of(position);
        Instant after = Instant.now();

        // Then
        assertThat(streamPosition.position()).isEqualTo(position);
        assertThat(streamPosition.transactionId()).isEqualTo("0");
        assertThat(streamPosition.occurredAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should create zero stream position")
    void shouldCreateZeroStreamPosition() {
        // When
        StreamPosition streamPosition = StreamPosition.zero();

        // Then
        assertThat(streamPosition.position()).isEqualTo(0L);
        assertThat(streamPosition.occurredAt()).isEqualTo(Instant.EPOCH);
        assertThat(streamPosition.transactionId()).isEqualTo("0");
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        long position = 100L;
        Instant occurredAt = Instant.now();
        String transactionId = "tx-123";

        // When
        StreamPosition sp1 = StreamPosition.of(position, occurredAt, transactionId);
        StreamPosition sp2 = StreamPosition.of(position, occurredAt, transactionId);

        // Then
        assertThat(sp1).isEqualTo(sp2);
        assertThat(sp1.hashCode()).isEqualTo(sp2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different positions")
    void shouldImplementEquals_WithDifferentPositions() {
        // Given
        Instant occurredAt = Instant.now();
        String transactionId = "tx-123";

        // When
        StreamPosition sp1 = StreamPosition.of(100L, occurredAt, transactionId);
        StreamPosition sp2 = StreamPosition.of(200L, occurredAt, transactionId);

        // Then
        assertThat(sp1).isNotEqualTo(sp2);
    }

    @Test
    @DisplayName("Should implement equals with different transaction IDs")
    void shouldImplementEquals_WithDifferentTransactionIds() {
        // Given
        long position = 100L;
        Instant occurredAt = Instant.now();

        // When
        StreamPosition sp1 = StreamPosition.of(position, occurredAt, "tx-123");
        StreamPosition sp2 = StreamPosition.of(position, occurredAt, "tx-456");

        // Then
        assertThat(sp1).isNotEqualTo(sp2);
    }

    @Test
    @DisplayName("Should handle zero position")
    void shouldHandleZeroPosition() {
        // When
        StreamPosition streamPosition = StreamPosition.of(0L, Instant.now(), "tx-123");

        // Then
        assertThat(streamPosition.position()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle max position")
    void shouldHandleMaxPosition() {
        // When
        StreamPosition streamPosition = StreamPosition.of(Long.MAX_VALUE, Instant.now(), "tx-123");

        // Then
        assertThat(streamPosition.position()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle null transaction ID")
    @SuppressWarnings("NullAway")
    void shouldHandleNullTransactionId_ShouldAllow() {
        // When - Direct constructor allows null
        StreamPosition streamPosition = new StreamPosition(100L, Instant.now(), null);

        // Then
        assertThat(streamPosition.transactionId()).isNull();
    }

    @Test
    @DisplayName("Should throw exception when position is negative")
    void shouldThrowException_WhenPositionIsNegative() {
        assertThatThrownBy(() -> StreamPosition.of(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Should handle null occurredAt")
    @SuppressWarnings("NullAway")
    void shouldHandleNullOccurredAt_ShouldAllow() {
        // When - Direct constructor allows null
        StreamPosition streamPosition = new StreamPosition(100L, null, "tx-123");

        // Then
        assertThat(streamPosition.occurredAt()).isNull();
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToString_Correctly() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // Then - Record auto-generates toString()
        String toString = streamPosition.toString();
        assertThat(toString)
                .contains("StreamPosition[")
                .contains("position=")
                .contains("occurredAt=")
                .contains("transactionId=");
    }
}
