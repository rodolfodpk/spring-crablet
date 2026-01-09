package com.crablet.outbox.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EventsPublishedMetric.
 * Tests validation logic and record properties.
 */
@DisplayName("EventsPublishedMetric Unit Tests")
class EventsPublishedMetricTest {

    @Test
    @DisplayName("Given valid input, when creating metric, then metric created successfully")
    void givenValidInput_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // Given
        String publisherName = "test-publisher";
        int count = 5;

        // When
        EventsPublishedMetric metric = new EventsPublishedMetric(publisherName, count);

        // Then
        assertThat(metric.publisherName()).isEqualTo(publisherName);
        assertThat(metric.count()).isEqualTo(count);
    }

    @Test
    @DisplayName("Given zero count, when creating metric, then metric created successfully")
    void givenZeroCount_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // Given
        String publisherName = "test-publisher";
        int count = 0;

        // When
        EventsPublishedMetric metric = new EventsPublishedMetric(publisherName, count);

        // Then
        assertThat(metric.publisherName()).isEqualTo(publisherName);
        assertThat(metric.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Given null publisher name, when creating metric, then IllegalArgumentException thrown")
    void givenNullPublisherName_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = null;
        int count = 5;

        // When & Then
        assertThatThrownBy(() -> new EventsPublishedMetric(publisherName, count))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Publisher name cannot be null or empty");
    }

    @Test
    @DisplayName("Given empty publisher name, when creating metric, then IllegalArgumentException thrown")
    void givenEmptyPublisherName_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = "";
        int count = 5;

        // When & Then
        assertThatThrownBy(() -> new EventsPublishedMetric(publisherName, count))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Publisher name cannot be null or empty");
    }

    @Test
    @DisplayName("Given negative count, when creating metric, then IllegalArgumentException thrown")
    void givenNegativeCount_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = "test-publisher";
        int count = -1;

        // When & Then
        assertThatThrownBy(() -> new EventsPublishedMetric(publisherName, count))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event count cannot be negative");
    }
}
