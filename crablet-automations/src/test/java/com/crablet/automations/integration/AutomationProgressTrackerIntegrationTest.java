package com.crablet.automations.integration;

import com.crablet.automations.adapter.AutomationProgressTracker;
import com.crablet.eventpoller.progress.ProcessorStatus;
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
 * Integration tests for {@link AutomationProgressTracker}.
 * Tests database persistence, status tracking, and error handling with real PostgreSQL.
 */
@SpringBootTest(classes = AutomationProgressTrackerIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("AutomationProgressTracker Integration Tests")
class AutomationProgressTrackerIntegrationTest extends AbstractAutomationsTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AutomationProgressTracker progressTracker;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
        progressTracker = new AutomationProgressTracker(dataSource);
    }

    @Test
    @DisplayName("Should return 0 for getLastPosition when automation not found")
    void shouldReturnZero_ForGetLastPosition_WhenAutomationNotFound() {
        // When
        long position = progressTracker.getLastPosition("non-existent-automation");

        // Then
        assertThat(position).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should update progress and persist position")
    void shouldUpdateProgress_AndPersistPosition() {
        // Given
        String automationName = "wallet-notification";

        // When
        progressTracker.updateProgress(automationName, 100L);

        // Then
        assertThat(progressTracker.getLastPosition(automationName)).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should update existing progress with ON CONFLICT")
    void shouldUpdateExistingProgress_WithOnConflict() {
        // Given
        String automationName = "wallet-notification";
        progressTracker.updateProgress(automationName, 50L);

        // When
        progressTracker.updateProgress(automationName, 150L);

        // Then
        assertThat(progressTracker.getLastPosition(automationName)).isEqualTo(150L);
    }

    @Test
    @DisplayName("Should record error without failing below threshold")
    void shouldRecordError_WithoutFailingBelowThreshold() {
        // Given
        String automationName = "wallet-notification";
        progressTracker.autoRegister(automationName, "instance-1");
        int maxErrors = 5;

        // When
        progressTracker.recordError(automationName, "Test error", maxErrors);

        // Then
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should set status to FAILED when error count reaches threshold")
    void shouldSetStatusToFailed_WhenErrorCountReachesThreshold() {
        // Given
        String automationName = "wallet-notification";
        progressTracker.autoRegister(automationName, "instance-1");
        int maxErrors = 2;

        // When
        progressTracker.recordError(automationName, "Error 1", maxErrors);
        progressTracker.recordError(automationName, "Error 2", maxErrors);

        // Then
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.FAILED);
    }

    @Test
    @DisplayName("Should reset error count and set status to ACTIVE")
    void shouldResetErrorCount_AndSetStatusToActive() {
        // Given
        String automationName = "wallet-notification";
        progressTracker.autoRegister(automationName, "instance-1");
        progressTracker.setStatus(automationName, ProcessorStatus.FAILED);

        // When
        progressTracker.resetErrorCount(automationName);

        // Then
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should return ACTIVE status when automation not found")
    void shouldReturnActiveStatus_WhenAutomationNotFound() {
        // When
        ProcessorStatus status = progressTracker.getStatus("non-existent-automation");

        // Then
        assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should set status correctly")
    void shouldSetStatus_Correctly() {
        // Given
        String automationName = "wallet-notification";
        progressTracker.autoRegister(automationName, "instance-1");

        // When
        progressTracker.setStatus(automationName, ProcessorStatus.PAUSED);

        // Then
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.PAUSED);
    }

    @Test
    @DisplayName("Should auto-register automation with instance ID")
    void shouldAutoRegisterAutomation_WithInstanceId() {
        // Given
        String automationName = "wallet-notification";

        // When
        progressTracker.autoRegister(automationName, "instance-123");

        // Then
        assertThat(progressTracker.getLastPosition(automationName)).isEqualTo(0L);
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should handle duplicate auto-registration with ON CONFLICT DO NOTHING")
    void shouldHandleDuplicateAutoRegistration_WithOnConflictDoNothing() {
        // Given
        String automationName = "wallet-notification";
        progressTracker.autoRegister(automationName, "instance-1");
        progressTracker.updateProgress(automationName, 100L);

        // When - Register again should not reset position
        progressTracker.autoRegister(automationName, "instance-2");

        // Then
        assertThat(progressTracker.getLastPosition(automationName)).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should handle all status values")
    void shouldHandleAllStatusValues() {
        // Given
        String automationName = "wallet-notification";
        progressTracker.autoRegister(automationName, "instance-1");

        // When & Then
        progressTracker.setStatus(automationName, ProcessorStatus.ACTIVE);
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.ACTIVE);

        progressTracker.setStatus(automationName, ProcessorStatus.PAUSED);
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.PAUSED);

        progressTracker.setStatus(automationName, ProcessorStatus.FAILED);
        assertThat(progressTracker.getStatus(automationName)).isEqualTo(ProcessorStatus.FAILED);
    }

    @Test
    @DisplayName("Should handle concurrent updates")
    void shouldHandleConcurrentUpdates() throws InterruptedException {
        // Given
        String automationName = "wallet-notification";
        progressTracker.autoRegister(automationName, "instance-1");

        // When
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                progressTracker.updateProgress(automationName, 100L + i);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                progressTracker.updateProgress(automationName, 200L + i);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Then - Last write wins, position should be from one of the thread's ranges
        long position = progressTracker.getLastPosition(automationName);
        assertThat(position)
            .as("Position should be from thread 1 (100-109) or thread 2 (200-209) range")
            .isIn(List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L,
                          200L, 201L, 202L, 203L, 204L, 205L, 206L, 207L, 208L, 209L));
    }

    @Configuration
    static class TestConfig {
        @Bean
        public javax.sql.DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(AbstractAutomationsTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractAutomationsTest.postgres.getUsername());
            dataSource.setPassword(AbstractAutomationsTest.postgres.getPassword());
            return dataSource;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }
    }
}
