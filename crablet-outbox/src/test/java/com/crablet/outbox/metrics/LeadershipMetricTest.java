package com.crablet.outbox.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LeadershipMetric.
 * Tests validation logic and record properties.
 */
@DisplayName("LeadershipMetric Unit Tests")
class LeadershipMetricTest {

    @Test
    @DisplayName("Given valid input when leader, when creating metric, then metric created successfully")
    void givenValidInputWhenLeader_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // Given
        String instanceId = "instance-1";
        boolean isLeader = true;

        // When
        LeadershipMetric metric = new LeadershipMetric(instanceId, isLeader);

        // Then
        assertThat(metric.instanceId()).isEqualTo(instanceId);
        assertThat(metric.isLeader()).isTrue();
    }

    @Test
    @DisplayName("Given valid input when not leader, when creating metric, then metric created successfully")
    void givenValidInputWhenNotLeader_whenCreatingMetric_thenMetricCreatedSuccessfully() {
        // Given
        String instanceId = "instance-1";
        boolean isLeader = false;

        // When
        LeadershipMetric metric = new LeadershipMetric(instanceId, isLeader);

        // Then
        assertThat(metric.instanceId()).isEqualTo(instanceId);
        assertThat(metric.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Given null instance ID, when creating metric, then IllegalArgumentException thrown")
    void givenNullInstanceId_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String instanceId = null;
        boolean isLeader = true;

        // When & Then
        assertThatThrownBy(() -> new LeadershipMetric(instanceId, isLeader))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Instance ID cannot be null or empty");
    }

    @Test
    @DisplayName("Given empty instance ID, when creating metric, then IllegalArgumentException thrown")
    void givenEmptyInstanceId_whenCreatingMetric_thenIllegalArgumentExceptionThrown() {
        // Given
        String instanceId = "";
        boolean isLeader = true;

        // When & Then
        assertThatThrownBy(() -> new LeadershipMetric(instanceId, isLeader))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Instance ID cannot be null or empty");
    }
}
