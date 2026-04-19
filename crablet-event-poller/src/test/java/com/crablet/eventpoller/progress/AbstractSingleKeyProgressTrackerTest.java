package com.crablet.eventpoller.progress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AbstractSingleKeyProgressTracker Unit Tests")
@Testcontainers
class AbstractSingleKeyProgressTrackerTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    private DataSource dataSource;
    private TestProgressTracker tracker;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
        tracker = new TestProgressTracker(dataSource);

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DROP TABLE IF EXISTS test_single_key_progress");
            connection.createStatement().execute("""
                CREATE TABLE test_single_key_progress (
                    processor_id VARCHAR(255) PRIMARY KEY,
                    instance_id VARCHAR(255),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    last_position BIGINT NOT NULL DEFAULT 0,
                    error_count INT NOT NULL DEFAULT 0,
                    last_error TEXT,
                    last_error_at TIMESTAMP WITH TIME ZONE,
                    last_updated_at TIMESTAMP WITH TIME ZONE
                )
                """);
        }
    }

    @Test
    @DisplayName("Constructor rejects null datasource")
    void constructorRejectsNullDatasource() {
        assertThatThrownBy(() -> new TestProgressTracker(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSource");
    }

    @Test
    @DisplayName("Missing progress defaults to zero and active")
    void missingProgressDefaultsToZeroAndActive() {
        assertThat(tracker.getLastPosition("processor-a")).isZero();
        assertThat(tracker.getStatus("processor-a")).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Auto register inserts active processor once")
    void autoRegisterInsertsActiveProcessorOnce() {
        tracker.autoRegister("processor-a", "instance-1");
        tracker.autoRegister("processor-a", "instance-2");

        assertThat(tracker.getStatus("processor-a")).isEqualTo(ProcessorStatus.ACTIVE);
        assertThat(tracker.getLastPosition("processor-a")).isZero();
    }

    @Test
    @DisplayName("Update progress inserts and updates position")
    void updateProgressInsertsAndUpdatesPosition() {
        tracker.updateProgress("processor-a", 42);
        tracker.updateProgress("processor-a", 99);

        assertThat(tracker.getLastPosition("processor-a")).isEqualTo(99);
    }

    @Test
    @DisplayName("Record error increments count and fails at max errors")
    void recordErrorIncrementsCountAndFailsAtMaxErrors() {
        tracker.autoRegister("processor-a", "instance-1");

        tracker.recordError("processor-a", "first", 2);
        assertThat(tracker.getStatus("processor-a")).isEqualTo(ProcessorStatus.ACTIVE);

        tracker.recordError("processor-a", "second", 2);
        assertThat(tracker.getStatus("processor-a")).isEqualTo(ProcessorStatus.FAILED);
    }

    @Test
    @DisplayName("Reset error count clears failure and reactivates")
    void resetErrorCountClearsFailureAndReactivates() {
        tracker.autoRegister("processor-a", "instance-1");
        tracker.recordError("processor-a", "boom", 1);

        tracker.resetErrorCount("processor-a");

        assertThat(tracker.getStatus("processor-a")).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Set status persists explicit state")
    void setStatusPersistsExplicitState() {
        tracker.autoRegister("processor-a", "instance-1");

        tracker.setStatus("processor-a", ProcessorStatus.PAUSED);

        assertThat(tracker.getStatus("processor-a")).isEqualTo(ProcessorStatus.PAUSED);
    }

    private static class TestProgressTracker extends AbstractSingleKeyProgressTracker {
        private TestProgressTracker(DataSource dataSource) {
            super(dataSource, "test_single_key_progress", "processor_id");
        }
    }
}
