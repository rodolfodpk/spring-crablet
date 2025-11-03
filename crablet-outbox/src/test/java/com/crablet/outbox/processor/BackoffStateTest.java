package com.crablet.outbox.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for BackoffState.
 * Tests exponential backoff logic, skip counting, and state management.
 */
@DisplayName("BackoffState Unit Tests")
class BackoffStateTest {

    @Test
    @DisplayName("Should initialize with zero emptyPollCount and skipCounter")
    void shouldInitialize_WithZeroCounters() {
        // Given
        int threshold = 3;
        int multiplier = 2;
        long pollingIntervalMs = 1000L;
        int maxBackoffSeconds = 60;

        // When
        BackoffState state = new BackoffState(threshold, multiplier, pollingIntervalMs, maxBackoffSeconds);

        // Then
        assertThat(state.getEmptyPollCount()).isEqualTo(0);
        assertThat(state.getCurrentSkipCounter()).isEqualTo(0);
        assertThat(state.shouldSkip()).isFalse();
    }

    @Test
    @DisplayName("Should not skip before threshold is reached")
    void shouldNotSkip_BeforeThresholdReached() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);

        // When - Record empty polls but stay below threshold
        state.recordEmpty(); // 1
        state.recordEmpty(); // 2

        // Then
        assertThat(state.getEmptyPollCount()).isEqualTo(2);
        assertThat(state.getCurrentSkipCounter()).isEqualTo(0);
        assertThat(state.shouldSkip()).isFalse();
    }

    @Test
    @DisplayName("Should start skipping after threshold is reached")
    void shouldStartSkipping_AfterThresholdReached() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);

        // When - Record enough empty polls to exceed threshold
        state.recordEmpty(); // 1
        state.recordEmpty(); // 2
        state.recordEmpty(); // 3
        state.recordEmpty(); // 4 (threshold + 1, exponent = 1)

        // Then
        assertThat(state.getEmptyPollCount()).isEqualTo(4);
        // After threshold, should calculate: 2^1 - 1 = 1 skip
        assertThat(state.getCurrentSkipCounter()).isEqualTo(1);
        assertThat(state.shouldSkip()).isTrue();
    }

    @Test
    @DisplayName("Should calculate exponential backoff correctly")
    void shouldCalculateExponentialBackoff_Correctly() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);

        // When - Record empty polls to trigger exponential backoff
        // Threshold is 3, so:
        // 4 empty polls: exponent=1, skips = 2^1 - 1 = 1
        // 5 empty polls: exponent=2, skips = 2^2 - 1 = 3
        // 6 empty polls: exponent=3, skips = 2^3 - 1 = 7
        for (int i = 0; i < 6; i++) {
            state.recordEmpty();
        }

        // Then - 6 polls means 3 above threshold, so exponent=3, skips = 2^3 - 1 = 7
        assertThat(state.getEmptyPollCount()).isEqualTo(6);
        assertThat(state.getCurrentSkipCounter()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should decrement skipCounter when shouldSkip is called")
    void shouldDecrementSkipCounter_WhenShouldSkipCalled() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);
        // Get to threshold + 1 to have 1 skip
        for (int i = 0; i < 4; i++) {
            state.recordEmpty();
        }

        // When
        boolean skip1 = state.shouldSkip();
        boolean skip2 = state.shouldSkip();

        // Then
        assertThat(skip1).isTrue(); // First call: skipCounter was 1
        assertThat(skip2).isFalse(); // Second call: skipCounter is now 0
        assertThat(state.getCurrentSkipCounter()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reset counters when recordSuccess is called")
    void shouldResetCounters_WhenRecordSuccessCalled() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);
        // Build up empty polls
        for (int i = 0; i < 5; i++) {
            state.recordEmpty();
        }
        assertThat(state.getEmptyPollCount()).isGreaterThan(0);
        assertThat(state.getCurrentSkipCounter()).isGreaterThan(0);

        // When
        state.recordSuccess();

        // Then
        assertThat(state.getEmptyPollCount()).isEqualTo(0);
        assertThat(state.getCurrentSkipCounter()).isEqualTo(0);
        assertThat(state.shouldSkip()).isFalse();
    }

    @Test
    @DisplayName("Should respect maxSkips limit")
    void shouldRespectMaxSkipsLimit() {
        // Given
        // maxSkips = (60 seconds * 1000) / 1000ms = 60 skips
        BackoffState state = new BackoffState(3, 2, 1000L, 60);

        // When - Record many empty polls to exceed maxSkips
        // After 10 empty polls: exponent = 7, skips = 2^7 - 1 = 127
        // But maxSkips = 60, so should cap at 60
        for (int i = 0; i < 10; i++) {
            state.recordEmpty();
        }

        // Then
        assertThat(state.getCurrentSkipCounter()).isEqualTo(60); // Capped at maxSkips
        assertThat(state.getCurrentSkipCounter()).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Should handle threshold = 0")
    void shouldHandleThreshold_EqualToZero() {
        // Given
        BackoffState state = new BackoffState(0, 2, 1000L, 60);

        // When - First empty poll should trigger backoff (threshold is 0)
        state.recordEmpty(); // 1 empty poll, exponent = 1, skips = 2^1 - 1 = 1

        // Then
        assertThat(state.getCurrentSkipCounter()).isEqualTo(1);
        assertThat(state.shouldSkip()).isTrue();
    }

    @Test
    @DisplayName("Should handle multiplier = 1 (no exponential growth)")
    void shouldHandleMultiplier_EqualToOne() {
        // Given
        BackoffState state = new BackoffState(3, 1, 1000L, 60);

        // When - Record empty polls
        // With multiplier=1: 2^exponent - 1 = 1^exponent - 1 = 0 (always)
        for (int i = 0; i < 5; i++) {
            state.recordEmpty();
        }

        // Then - With multiplier=1, 2^exponent - 1 = 1 - 1 = 0
        // Actually wait, the code uses Math.pow(multiplier, exponent), so:
        // multiplier=1, exponent=2: 1^2 - 1 = 0
        assertThat(state.getEmptyPollCount()).isEqualTo(5);
        // Should still calculate, but result will be 0 skips with multiplier=1
    }

    @Test
    @DisplayName("Should handle very large maxSkips")
    void shouldHandleVeryLargeMaxSkips() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 3600); // 1 hour = 3600 skips

        // When
        for (int i = 0; i < 10; i++) {
            state.recordEmpty();
        }

        // Then - Should not exceed maxSkips (3600)
        assertThat(state.getCurrentSkipCounter()).isLessThanOrEqualTo(3600);
    }

    @Test
    @DisplayName("Should calculate maxSkips correctly from polling interval")
    void shouldCalculateMaxSkips_CorrectlyFromPollingInterval() {
        // Given
        long pollingIntervalMs = 500L; // 0.5 seconds
        int maxBackoffSeconds = 30;

        // When
        BackoffState state = new BackoffState(3, 2, pollingIntervalMs, maxBackoffSeconds);
        // maxSkips = (30 * 1000) / 500 = 60

        // Then - Record enough to exceed calculated maxSkips
        for (int i = 0; i < 15; i++) {
            state.recordEmpty();
        }

        // Then - Should be capped at 60
        assertThat(state.getCurrentSkipCounter()).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Should allow immediate polling after success")
    void shouldAllowImmediatePolling_AfterSuccess() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);
        // Build up skips
        for (int i = 0; i < 5; i++) {
            state.recordEmpty();
        }
        assertThat(state.shouldSkip()).isTrue();

        // When
        state.recordSuccess();

        // Then
        assertThat(state.shouldSkip()).isFalse();
        // Next empty poll should start counting from 0 again
        state.recordEmpty();
        assertThat(state.getEmptyPollCount()).isEqualTo(1);
        assertThat(state.shouldSkip()).isFalse();
    }

    @Test
    @DisplayName("Should handle multiple consecutive empty polls beyond threshold")
    void shouldHandleMultipleConsecutiveEmptyPolls_BeyondThreshold() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);

        // When - Record many empty polls
        for (int i = 0; i < 8; i++) {
            state.recordEmpty();
        }
        // 8 empty polls: exponent = 5, skips = 2^5 - 1 = 31

        // Then
        assertThat(state.getEmptyPollCount()).isEqualTo(8);
        assertThat(state.getCurrentSkipCounter()).isEqualTo(31);
        assertThat(state.shouldSkip()).isTrue();
    }

    @Test
    @DisplayName("Should get correct emptyPollCount after multiple operations")
    void shouldGetCorrectEmptyPollCount_AfterMultipleOperations() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);

        // When
        state.recordEmpty();
        state.recordEmpty();
        state.recordEmpty();
        state.recordSuccess();
        state.recordEmpty();

        // Then
        assertThat(state.getEmptyPollCount()).isEqualTo(1); // Reset to 0, then 1
    }

    @Test
    @DisplayName("Should get correct currentSkipCounter after decrements")
    void shouldGetCorrectCurrentSkipCounter_AfterDecrements() {
        // Given
        BackoffState state = new BackoffState(3, 2, 1000L, 60);
        // Get to 4 empty polls: skipCounter = 1
        for (int i = 0; i < 4; i++) {
            state.recordEmpty();
        }

        // When
        int beforeSkip = state.getCurrentSkipCounter();
        state.shouldSkip(); // Decrements skipCounter
        int afterSkip = state.getCurrentSkipCounter();

        // Then
        assertThat(beforeSkip).isEqualTo(1);
        assertThat(afterSkip).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle edge case: threshold = maxSkips calculation")
    void shouldHandleEdgeCase_ThresholdEqualToMaxSkips() {
        // Given
        // Very small polling interval, very small maxBackoffSeconds
        // maxSkips = (1 * 1000) / 1000 = 1
        BackoffState state = new BackoffState(3, 2, 1000L, 1);

        // When
        for (int i = 0; i < 10; i++) {
            state.recordEmpty();
        }

        // Then - Should cap at maxSkips (1)
        assertThat(state.getCurrentSkipCounter()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should handle very small polling interval")
    void shouldHandleVerySmallPollingInterval() {
        // Given
        // 100ms polling, 60s max = maxSkips = (60 * 1000) / 100 = 600
        BackoffState state = new BackoffState(3, 2, 100L, 60);

        // When
        for (int i = 0; i < 10; i++) {
            state.recordEmpty();
        }

        // Then
        assertThat(state.getCurrentSkipCounter()).isLessThanOrEqualTo(600);
    }
}

