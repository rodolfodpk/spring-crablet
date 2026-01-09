package com.crablet.outbox.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProcessingCycleMetric.
 * Tests that the metric can be instantiated (simple marker record).
 */
@DisplayName("ProcessingCycleMetric Unit Tests")
class ProcessingCycleMetricTest {

    @Test
    @DisplayName("Given no input, when creating metric, then metric created successfully")
    void givenNoInput_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // When
        ProcessingCycleMetric metric = new ProcessingCycleMetric();

        // Then
        assertThat(metric).isNotNull();
    }
}
