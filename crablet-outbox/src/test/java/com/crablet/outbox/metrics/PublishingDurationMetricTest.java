package com.crablet.outbox.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PublishingDurationMetric.
 * Tests validation logic and record properties.
 */
@DisplayName("PublishingDurationMetric Unit Tests")
class PublishingDurationMetricTest {

    @Test
    @DisplayName("Given valid input, when creating metric, then metric created successfully")
    void givenValidInput_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // Given
        String publisherName = "test-publisher";
        Duration duration = Duration.ofMillis(100);

        // When
        PublishingDurationMetric metric = new PublishingDurationMetric(publisherName, duration);

        // Then
        assertThat(metric.publisherName()).isEqualTo(publisherName);
        assertThat(metric.duration()).isEqualTo(duration);
    }

    @Test
    @DisplayName("Given zero duration, when creating metric, then metric created successfully")
    void givenZeroDuration_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // Given
        String publisherName = "test-publisher";
        Duration duration = Duration.ZERO;

        // When
        PublishingDurationMetric metric = new PublishingDurationMetric(publisherName, duration);

        // Then
        assertThat(metric.publisherName()).isEqualTo(publisherName);
        assertThat(metric.duration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Given null publisher name, when creating metric, then IllegalArgumentException thrown")
    void givenNullPublisherName_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = null;
        Duration duration = Duration.ofMillis(100);

        // When & Then
        assertThatThrownBy(() -> new PublishingDurationMetric(publisherName, duration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Publisher name cannot be null or empty");
    }

    @Test
    @DisplayName("Given empty publisher name, when creating metric, then IllegalArgumentException thrown")
    void givenEmptyPublisherName_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = "";
        Duration duration = Duration.ofMillis(100);

        // When & Then
        assertThatThrownBy(() -> new PublishingDurationMetric(publisherName, duration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Publisher name cannot be null or empty");
    }

    @Test
    @DisplayName("Given null duration, when creating metric, then IllegalArgumentException thrown")
    void givenNullDuration_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = "test-publisher";
        Duration duration = null;

        // When & Then
        assertThatThrownBy(() -> new PublishingDurationMetric(publisherName, duration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duration cannot be null");
    }

    @Test
    @DisplayName("Given negative duration, when creating metric, then IllegalArgumentException thrown")
    void givenNegativeDuration_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String publisherName = "test-publisher";
        Duration duration = Duration.ofMillis(-100);

        // When & Then
        assertThatThrownBy(() -> new PublishingDurationMetric(publisherName, duration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duration cannot be negative");
    }
}
