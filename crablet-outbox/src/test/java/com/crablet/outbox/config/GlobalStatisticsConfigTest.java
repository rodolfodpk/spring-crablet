package com.crablet.outbox.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalStatisticsConfig.
 * Tests default values and getters/setters.
 */
@DisplayName("GlobalStatisticsConfig Unit Tests")
class GlobalStatisticsConfigTest {

    @Test
    @DisplayName("Should have default values")
    void shouldHaveDefaultValues() {
        // When
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getLogIntervalSeconds()).isEqualTo(30L);
        assertThat(config.getLogLevel()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("Should set and get enabled")
    void shouldSetAndGetEnabled() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setEnabled(false);

        // Then
        assertThat(config.isEnabled()).isFalse();

        // When
        config.setEnabled(true);

        // Then
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should set and get logIntervalSeconds")
    void shouldSetAndGetLogIntervalSeconds() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setLogIntervalSeconds(60L);

        // Then
        assertThat(config.getLogIntervalSeconds()).isEqualTo(60L);

        // When
        config.setLogIntervalSeconds(10L);

        // Then
        assertThat(config.getLogIntervalSeconds()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Should set and get logLevel")
    void shouldSetAndGetLogLevel() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setLogLevel("DEBUG");

        // Then
        assertThat(config.getLogLevel()).isEqualTo("DEBUG");

        // When
        config.setLogLevel("WARN");

        // Then
        assertThat(config.getLogLevel()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("Should handle zero logIntervalSeconds")
    void shouldHandleZeroLogIntervalSeconds() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setLogIntervalSeconds(0L);

        // Then - No validation, so zero is allowed
        assertThat(config.getLogIntervalSeconds()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle negative logIntervalSeconds")
    void shouldHandleNegativeLogIntervalSeconds() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setLogIntervalSeconds(-1L);

        // Then - No validation, so negative is allowed
        assertThat(config.getLogIntervalSeconds()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("Should handle null logLevel")
    void shouldHandleNullLogLevel_ShouldAllow() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setLogLevel(null);

        // Then - No validation, so null is allowed
        assertThat(config.getLogLevel()).isNull();
    }

    @Test
    @DisplayName("Should handle empty logLevel")
    void shouldHandleEmptyLogLevel() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setLogLevel("");

        // Then
        assertThat(config.getLogLevel()).isEmpty();
    }

    @Test
    @DisplayName("Should handle very large logIntervalSeconds")
    void shouldHandleVeryLargeLogIntervalSeconds() {
        // Given
        GlobalStatisticsConfig config = new GlobalStatisticsConfig();

        // When
        config.setLogIntervalSeconds(Long.MAX_VALUE);

        // Then
        assertThat(config.getLogIntervalSeconds()).isEqualTo(Long.MAX_VALUE);
    }
}

