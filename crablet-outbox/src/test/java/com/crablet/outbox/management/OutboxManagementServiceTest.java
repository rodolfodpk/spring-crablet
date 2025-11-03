package com.crablet.outbox.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OutboxManagementService record classes.
 * Tests PublisherStatus and BackoffInfo records.
 * 
 * Note: Service methods with database dependencies are tested in integration tests.
 */
@DisplayName("OutboxManagementService Record Classes Unit Tests")
class OutboxManagementServiceTest {

    @Test
    @DisplayName("PublisherStatus should have all fields")
    void publisherStatus_ShouldHaveAllFields() {
        // Given
        Instant now = Instant.now();
        OutboxManagementService.PublisherStatus status = new OutboxManagementService.PublisherStatus(
                "publisher1",
                "ACTIVE",
                100L,
                now,
                0,
                null,
                now
        );

        // Then
        assertThat(status.publisherName()).isEqualTo("publisher1");
        assertThat(status.status()).isEqualTo("ACTIVE");
        assertThat(status.lastPosition()).isEqualTo(100L);
        assertThat(status.lastPublishedAt()).isEqualTo(now);
        assertThat(status.errorCount()).isEqualTo(0);
        assertThat(status.lastError()).isNull();
        assertThat(status.updatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("PublisherStatus isActive should return true for ACTIVE status")
    void publisherStatus_IsActive_ShouldReturnTrue_ForActiveStatus() {
        // Given
        OutboxManagementService.PublisherStatus status = new OutboxManagementService.PublisherStatus(
                "publisher1",
                "ACTIVE",
                100L,
                Instant.now(),
                0,
                null,
                Instant.now()
        );

        // When & Then
        assertThat(status.isActive()).isTrue();
        assertThat(status.isPaused()).isFalse();
        assertThat(status.isFailed()).isFalse();
    }

    @Test
    @DisplayName("PublisherStatus isPaused should return true for PAUSED status")
    void publisherStatus_IsPaused_ShouldReturnTrue_ForPausedStatus() {
        // Given
        OutboxManagementService.PublisherStatus status = new OutboxManagementService.PublisherStatus(
                "publisher1",
                "PAUSED",
                100L,
                Instant.now(),
                0,
                null,
                Instant.now()
        );

        // When & Then
        assertThat(status.isActive()).isFalse();
        assertThat(status.isPaused()).isTrue();
        assertThat(status.isFailed()).isFalse();
    }

    @Test
    @DisplayName("PublisherStatus isFailed should return true for FAILED status")
    void publisherStatus_IsFailed_ShouldReturnTrue_ForFailedStatus() {
        // Given
        OutboxManagementService.PublisherStatus status = new OutboxManagementService.PublisherStatus(
                "publisher1",
                "FAILED",
                100L,
                Instant.now(),
                5,
                "Connection error",
                Instant.now()
        );

        // When & Then
        assertThat(status.isActive()).isFalse();
        assertThat(status.isPaused()).isFalse();
        assertThat(status.isFailed()).isTrue();
    }

    @Test
    @DisplayName("PublisherStatus should handle null lastPublishedAt")
    void publisherStatus_ShouldHandleNullLastPublishedAt() {
        // Given
        OutboxManagementService.PublisherStatus status = new OutboxManagementService.PublisherStatus(
                "publisher1",
                "ACTIVE",
                0L,
                null, // null lastPublishedAt
                0,
                null,
                Instant.now()
        );

        // Then
        assertThat(status.lastPublishedAt()).isNull();
        assertThat(status.isActive()).isTrue();
    }

    @Test
    @DisplayName("PublisherStatus should handle different status values")
    void publisherStatus_ShouldHandleDifferentStatusValues() {
        // Test various status values
        OutboxManagementService.PublisherStatus status1 = new OutboxManagementService.PublisherStatus(
                "publisher1", "ACTIVE", 100L, Instant.now(), 0, null, Instant.now());
        OutboxManagementService.PublisherStatus status2 = new OutboxManagementService.PublisherStatus(
                "publisher1", "PAUSED", 100L, Instant.now(), 0, null, Instant.now());
        OutboxManagementService.PublisherStatus status3 = new OutboxManagementService.PublisherStatus(
                "publisher1", "FAILED", 100L, Instant.now(), 5, "Error", Instant.now());
        OutboxManagementService.PublisherStatus status4 = new OutboxManagementService.PublisherStatus(
                "publisher1", "UNKNOWN", 100L, Instant.now(), 0, null, Instant.now());

        // Then
        assertThat(status1.isActive()).isTrue();
        assertThat(status2.isPaused()).isTrue();
        assertThat(status3.isFailed()).isTrue();
        assertThat(status4.isActive()).isFalse();
        assertThat(status4.isPaused()).isFalse();
        assertThat(status4.isFailed()).isFalse();
    }

    @Test
    @DisplayName("PublisherStatus should implement equals and hashCode")
    void publisherStatus_ShouldImplementEqualsAndHashCode() {
        // Given
        Instant now = Instant.now();
        OutboxManagementService.PublisherStatus status1 = new OutboxManagementService.PublisherStatus(
                "publisher1", "ACTIVE", 100L, now, 0, null, now);
        OutboxManagementService.PublisherStatus status2 = new OutboxManagementService.PublisherStatus(
                "publisher1", "ACTIVE", 100L, now, 0, null, now);
        OutboxManagementService.PublisherStatus status3 = new OutboxManagementService.PublisherStatus(
                "publisher2", "ACTIVE", 100L, now, 0, null, now);

        // Then
        assertThat(status1).isEqualTo(status2);
        assertThat(status1.hashCode()).isEqualTo(status2.hashCode());
        assertThat(status1).isNotEqualTo(status3);
    }

    @Test
    @DisplayName("BackoffInfo should have all fields")
    void backoffInfo_ShouldHaveAllFields() {
        // Given
        OutboxManagementService.BackoffInfo info = new OutboxManagementService.BackoffInfo(5, 3);

        // Then
        assertThat(info.emptyPollCount()).isEqualTo(5);
        assertThat(info.currentSkipCounter()).isEqualTo(3);
    }

    @Test
    @DisplayName("BackoffInfo isBackedOff should return true when skipCounter > 0")
    void backoffInfo_IsBackedOff_ShouldReturnTrue_WhenSkipCounterGreaterThanZero() {
        // Given
        OutboxManagementService.BackoffInfo info1 = new OutboxManagementService.BackoffInfo(5, 3);
        OutboxManagementService.BackoffInfo info2 = new OutboxManagementService.BackoffInfo(10, 1);
        OutboxManagementService.BackoffInfo info3 = new OutboxManagementService.BackoffInfo(0, 0);

        // Then
        assertThat(info1.isBackedOff()).isTrue();
        assertThat(info2.isBackedOff()).isTrue();
        assertThat(info3.isBackedOff()).isFalse();
    }

    @Test
    @DisplayName("BackoffInfo isBackedOff should return false when skipCounter is 0")
    void backoffInfo_IsBackedOff_ShouldReturnFalse_WhenSkipCounterIsZero() {
        // Given
        OutboxManagementService.BackoffInfo info = new OutboxManagementService.BackoffInfo(10, 0);

        // Then
        assertThat(info.isBackedOff()).isFalse();
    }

    @Test
    @DisplayName("BackoffInfo should handle zero values")
    void backoffInfo_ShouldHandleZeroValues() {
        // Given
        OutboxManagementService.BackoffInfo info = new OutboxManagementService.BackoffInfo(0, 0);

        // Then
        assertThat(info.emptyPollCount()).isEqualTo(0);
        assertThat(info.currentSkipCounter()).isEqualTo(0);
        assertThat(info.isBackedOff()).isFalse();
    }

    @Test
    @DisplayName("BackoffInfo should implement equals and hashCode")
    void backoffInfo_ShouldImplementEqualsAndHashCode() {
        // Given
        OutboxManagementService.BackoffInfo info1 = new OutboxManagementService.BackoffInfo(5, 3);
        OutboxManagementService.BackoffInfo info2 = new OutboxManagementService.BackoffInfo(5, 3);
        OutboxManagementService.BackoffInfo info3 = new OutboxManagementService.BackoffInfo(10, 3);

        // Then
        assertThat(info1).isEqualTo(info2);
        assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
        assertThat(info1).isNotEqualTo(info3);
    }

    @Test
    @DisplayName("BackoffInfo should handle large values")
    void backoffInfo_ShouldHandleLargeValues() {
        // Given
        OutboxManagementService.BackoffInfo info = new OutboxManagementService.BackoffInfo(1000, 500);

        // Then
        assertThat(info.emptyPollCount()).isEqualTo(1000);
        assertThat(info.currentSkipCounter()).isEqualTo(500);
        assertThat(info.isBackedOff()).isTrue();
    }
}

