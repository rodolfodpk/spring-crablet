package com.crablet.eventpoller.progress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/test/test_single_key_progress.sql"));
        }
    }

    @Test
    @DisplayName("Constructor rejects null datasource")
    @SuppressWarnings("NullAway")
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
