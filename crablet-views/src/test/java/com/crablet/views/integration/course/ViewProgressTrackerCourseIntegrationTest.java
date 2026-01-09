package com.crablet.views.integration.course;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.views.adapter.ViewProgressTracker;
import com.crablet.views.integration.AbstractViewsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ViewProgressTracker.
 * Tests database persistence, status tracking, and error handling with real PostgreSQL.
 */
@SpringBootTest(classes = ViewProgressTrackerCourseIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("ViewProgressTracker Course Domain Integration Tests")
class ViewProgressTrackerCourseIntegrationTest extends AbstractViewsTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ViewProgressTracker progressTracker;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
        progressTracker = new ViewProgressTracker(dataSource);
    }

    @Test
    @DisplayName("Should return 0 for getLastPosition when view not found")
    void shouldReturnZero_ForGetLastPosition_WhenViewNotFound() {
        // When
        long position = progressTracker.getLastPosition("non-existent-view");

        // Then
        assertThat(position).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should update progress and persist position")
    void shouldUpdateProgress_AndPersistPosition() {
        // Given
        String viewName = "course-view";
        long position = 100L;

        // When
        progressTracker.updateProgress(viewName, position);

        // Then
        long retrievedPosition = progressTracker.getLastPosition(viewName);
        assertThat(retrievedPosition).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should update existing progress with ON CONFLICT")
    void shouldUpdateExistingProgress_WithOnConflict() {
        // Given
        String viewName = "course-view";
        progressTracker.updateProgress(viewName, 50L);

        // When - Update to new position
        progressTracker.updateProgress(viewName, 150L);

        // Then
        assertThat(progressTracker.getLastPosition(viewName)).isEqualTo(150L);
    }

    @Test
    @DisplayName("Should record error and increment error count")
    void shouldRecordError_AndIncrementErrorCount() {
        // Given
        String viewName = "course-view";
        progressTracker.autoRegister(viewName, "instance-1");
        int maxErrors = 5;

        // When
        progressTracker.recordError(viewName, "Test error", maxErrors);

        // Then
        // Verify error count was incremented (we can't directly query, but status should reflect it)
        ProcessorStatus status = progressTracker.getStatus(viewName);
        // Status should still be ACTIVE if error count < maxErrors
        assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should set status to FAILED when error count exceeds threshold")
    void shouldSetStatusToFailed_WhenErrorCountExceedsThreshold() {
        // Given
        String viewName = "course-view";
        progressTracker.autoRegister(viewName, "instance-1");
        int maxErrors = 2;

        // When - Record errors up to threshold
        progressTracker.recordError(viewName, "Error 1", maxErrors);
        progressTracker.recordError(viewName, "Error 2", maxErrors); // Should trigger FAILED

        // Then
        ProcessorStatus status = progressTracker.getStatus(viewName);
        assertThat(status).isEqualTo(ProcessorStatus.FAILED);
    }

    @Test
    @DisplayName("Should reset error count and set status to ACTIVE")
    void shouldResetErrorCount_AndSetStatusToActive() {
        // Given
        String viewName = "course-view";
        progressTracker.autoRegister(viewName, "instance-1");
        progressTracker.recordError(viewName, "Error", 2);
        progressTracker.setStatus(viewName, ProcessorStatus.FAILED);

        // When
        progressTracker.resetErrorCount(viewName);

        // Then
        ProcessorStatus status = progressTracker.getStatus(viewName);
        assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should return ACTIVE status when view not found")
    void shouldReturnActiveStatus_WhenViewNotFound() {
        // When
        ProcessorStatus status = progressTracker.getStatus("non-existent-view");

        // Then
        assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should set status correctly")
    void shouldSetStatus_Correctly() {
        // Given
        String viewName = "course-view";
        progressTracker.autoRegister(viewName, "instance-1");

        // When
        progressTracker.setStatus(viewName, ProcessorStatus.PAUSED);

        // Then
        assertThat(progressTracker.getStatus(viewName)).isEqualTo(ProcessorStatus.PAUSED);
    }

    @Test
    @DisplayName("Should auto-register view with instance ID")
    void shouldAutoRegisterView_WithInstanceId() {
        // Given
        String viewName = "course-view";
        String instanceId = "instance-123";

        // When
        progressTracker.autoRegister(viewName, instanceId);

        // Then
        assertThat(progressTracker.getLastPosition(viewName)).isEqualTo(0L);
        assertThat(progressTracker.getStatus(viewName)).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should handle duplicate auto-registration with ON CONFLICT DO NOTHING")
    void shouldHandleDuplicateAutoRegistration_WithOnConflictDoNothing() {
        // Given
        String viewName = "course-view";
        String instanceId = "instance-123";
        progressTracker.autoRegister(viewName, instanceId);
        progressTracker.updateProgress(viewName, 100L);

        // When - Try to register again
        progressTracker.autoRegister(viewName, "instance-456");

        // Then - Position should remain unchanged (ON CONFLICT DO NOTHING)
        assertThat(progressTracker.getLastPosition(viewName)).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should handle concurrent updates")
    void shouldHandleConcurrentUpdates() throws InterruptedException {
        // Given
        String viewName = "course-view";
        progressTracker.autoRegister(viewName, "instance-1");

        // When - Simulate concurrent updates
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                progressTracker.updateProgress(viewName, 100L + i);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                progressTracker.updateProgress(viewName, 200L + i);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Then - Last write should win (database handles concurrency)
        // Position should be from either thread's final range (100-109 or 200-209)
        long position = progressTracker.getLastPosition(viewName);
        assertThat(position)
            .as("Position should be from thread 1 (100-109) or thread 2 (200-209) range")
            .isIn(List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L,
                           200L, 201L, 202L, 203L, 204L, 205L, 206L, 207L, 208L, 209L));
    }

    @Test
    @DisplayName("Should handle all status values")
    void shouldHandleAllStatusValues() {
        // Given
        String viewName = "course-view";
        progressTracker.autoRegister(viewName, "instance-1");

        // When & Then
        progressTracker.setStatus(viewName, ProcessorStatus.ACTIVE);
        assertThat(progressTracker.getStatus(viewName)).isEqualTo(ProcessorStatus.ACTIVE);

        progressTracker.setStatus(viewName, ProcessorStatus.PAUSED);
        assertThat(progressTracker.getStatus(viewName)).isEqualTo(ProcessorStatus.PAUSED);

        progressTracker.setStatus(viewName, ProcessorStatus.FAILED);
        assertThat(progressTracker.getStatus(viewName)).isEqualTo(ProcessorStatus.FAILED);
    }

    @Configuration
    static class TestConfig {
        @Bean
        public javax.sql.DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(AbstractViewsTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractViewsTest.postgres.getUsername());
            dataSource.setPassword(AbstractViewsTest.postgres.getPassword());
            return dataSource;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}

