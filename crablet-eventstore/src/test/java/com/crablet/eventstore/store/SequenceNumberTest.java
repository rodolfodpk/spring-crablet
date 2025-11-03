package com.crablet.eventstore.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SequenceNumber value object.
 * Tests factory methods, validation, equality/hashCode, and edge cases.
 */
@DisplayName("SequenceNumber Unit Tests")
class SequenceNumberTest {

    @Test
    @DisplayName("Should create SequenceNumber with positive value")
    void shouldCreateSequenceNumber_WithPositiveValue() {
        // Given
        long value = 100L;

        // When
        SequenceNumber sequenceNumber = SequenceNumber.of(value);

        // Then
        assertThat(sequenceNumber.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("Should create SequenceNumber with zero value")
    void shouldCreateSequenceNumber_WithZero() {
        // Given
        long value = 0L;

        // When
        SequenceNumber sequenceNumber = SequenceNumber.of(value);

        // Then
        assertThat(sequenceNumber.value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create SequenceNumber using zero factory")
    void shouldCreateSequenceNumber_UsingZeroFactory() {
        // When
        SequenceNumber sequenceNumber = SequenceNumber.zero();

        // Then
        assertThat(sequenceNumber.value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should throw exception when negative value")
    void shouldThrowException_WhenNegativeValue() {
        // Given
        long negativeValue = -1L;

        // When & Then
        assertThatThrownBy(() -> SequenceNumber.of(negativeValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SequenceNumber cannot be negative");
    }

    @Test
    @DisplayName("Should throw exception when large negative value")
    void shouldThrowException_WhenLargeNegativeValue() {
        // Given
        long largeNegativeValue = Long.MIN_VALUE;

        // When & Then
        assertThatThrownBy(() -> SequenceNumber.of(largeNegativeValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SequenceNumber cannot be negative");
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        long value = 100L;

        // When
        SequenceNumber seq1 = SequenceNumber.of(value);
        SequenceNumber seq2 = SequenceNumber.of(value);

        // Then
        assertThat(seq1).isEqualTo(seq2);
        assertThat(seq1.hashCode()).isEqualTo(seq2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different values")
    void shouldImplementEquals_WithDifferentValues() {
        // When
        SequenceNumber seq1 = SequenceNumber.of(100L);
        SequenceNumber seq2 = SequenceNumber.of(200L);

        // Then
        assertThat(seq1).isNotEqualTo(seq2);
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToString_Correctly() {
        // Given
        long value = 100L;

        // When
        SequenceNumber sequenceNumber = SequenceNumber.of(value);

        // Then
        assertThat(sequenceNumber.toString()).isEqualTo("100");
    }

    @Test
    @DisplayName("Should handle max long value")
    void shouldHandleMaxLongValue() {
        // Given
        long maxValue = Long.MAX_VALUE;

        // When
        SequenceNumber sequenceNumber = SequenceNumber.of(maxValue);

        // Then
        assertThat(sequenceNumber.value()).isEqualTo(Long.MAX_VALUE);
        assertThat(sequenceNumber.toString()).isEqualTo(String.valueOf(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("Should handle zero value")
    void shouldHandleZeroValue() {
        // When
        SequenceNumber sequenceNumber = SequenceNumber.zero();

        // Then
        assertThat(sequenceNumber.value()).isEqualTo(0);
        assertThat(sequenceNumber.toString()).isEqualTo("0");
    }

    @Test
    @DisplayName("Should verify zero() and of(0) are equal")
    void shouldVerifyZeroAndOfZeroAreEqual() {
        // When
        SequenceNumber zero1 = SequenceNumber.zero();
        SequenceNumber zero2 = SequenceNumber.of(0);

        // Then
        assertThat(zero1).isEqualTo(zero2);
        assertThat(zero1.hashCode()).isEqualTo(zero2.hashCode());
    }
}

