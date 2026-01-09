package com.crablet.outbox.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OutboxErrorMetric.
 * Tests validation logic and record properties.
 */
@DisplayName("OutboxErrorMetric Unit Tests")
class OutboxErrorMetricTest {

    @Test
    @DisplayName("Given valid input, when creating metric, then metric created successfully")
    void givenValidInput_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // Given
        String publisherName = "test-publisher";

        // When
        OutboxErrorMetric metric = new OutboxErrorMetric(publisherName);

        // Then
        assertThat(metric.publisherName()).isEqualTo(publisherName);
    }

    @Test
    @DisplayName("Given null publisher name, when creating metric, then IllegalArgumentException thrown")
    void givenNullPublisherName_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = null;

        // When & Then
        assertThatThrownBy(() -> new OutboxErrorMetric(publisherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Publisher name cannot be null or empty");
    }

    @Test
    @DisplayName("Given empty publisher name, when creating metric, then IllegalArgumentException thrown")
    void givenEmptyPublisherName_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = "";

        // When & Then
        assertThatThrownBy(() -> new OutboxErrorMetric(publisherName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Publisher name cannot be null or empty");
    }
}
