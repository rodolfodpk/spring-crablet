package com.crablet.eventprocessor.processor;

import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventprocessor.backoff.BackoffState;
import com.crablet.eventprocessor.integration.AbstractEventProcessorTest;
import com.crablet.eventprocessor.leader.LeaderElector;
import com.crablet.eventprocessor.leader.LeaderElectorImpl;
import com.crablet.eventprocessor.progress.ProgressTracker;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for EventProcessorImpl.
 * Tests core processing logic, progress tracking, status handling, and error management with real database.
 */
@SpringBootTest(classes = EventProcessorImplIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("EventProcessorImpl Integration Tests")
class EventProcessorImplIntegrationTest extends AbstractEventProcessorTest {

    @Autowired
    private EventProcessor<TestProcessorConfig, String> eventProcessor;

    @Autowired
    private Map<String, TestProcessorConfig> processorConfigs;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestProgressTracker progressTracker;

    @Autowired
    private TestEventHandler eventHandler;

    @BeforeEach
    void setUp() {
        // Stop any running schedulers to prevent background processing
        eventProcessor.stop();
        
        // Clean database to ensure test isolation
        cleanDatabase(jdbcTemplate);
        
        // Reset handler state
        eventHandler.reset();
        
        // Reset progress tracker state
        progressTracker.positions.clear();
        progressTracker.statuses.clear();
        progressTracker.errorCounts.clear();
    }

    @Test
    @DisplayName("Should process events and update progress")
    void shouldProcessEvents_AndUpdateProgress() {
        // Given - Verify database is clean
        Long eventCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Long.class);
        assertThat(eventCount).as("Database should be clean from setUp()").isEqualTo(0L);
        
        // Given - Events in store
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent1")
                .data("{\"id\":1}".getBytes())
                .build(),
            AppendEvent.builder("TestEvent2")
                .data("{\"id\":2}".getBytes())
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // Verify only 2 events were created
        eventCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Long.class);
        assertThat(eventCount).as("Should have exactly 2 events").isEqualTo(2L);
        
        // Verify event positions are 1 and 2
        List<Long> positions = jdbcTemplate.queryForList("SELECT position FROM events ORDER BY position", Long.class);
        assertThat(positions).as("Events should have positions 1 and 2").containsExactly(1L, 2L);

        // When - Process
        int processed = eventProcessor.process("test-processor");

        // Then
        assertThat(processed).isEqualTo(2);
        assertThat(eventHandler.getHandledCount()).isEqualTo(2);
        // The position should match the last event's position (which should be 2)
        assertThat(progressTracker.getLastPosition("test-processor")).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should auto-register processor on first process")
    void shouldAutoRegisterProcessor_OnFirstProcess() {
        // Given - No processor registered yet
        assertThat(progressTracker.getLastPosition("test-processor")).isEqualTo(0L);

        // When - Process (even with no events)
        int processed = eventProcessor.process("test-processor");

        // Then - Processor should be auto-registered
        assertThat(processed).isEqualTo(0);
        ProcessorStatus status = progressTracker.getStatus("test-processor");
        assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should skip processing when processor is paused")
    void shouldSkipProcessing_WhenProcessorIsPaused() {
        // Given - Processor is paused
        progressTracker.setStatus("test-processor", ProcessorStatus.PAUSED);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent")
                .data("{\"id\":1}".getBytes())
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Process
        int processed = eventProcessor.process("test-processor");

        // Then - Should return 0 (skipped)
        assertThat(processed).isEqualTo(0);
        assertThat(eventHandler.getHandledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should skip processing when processor is failed")
    void shouldSkipProcessing_WhenProcessorIsFailed() {
        // Given - Processor is failed
        progressTracker.setStatus("test-processor", ProcessorStatus.FAILED);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent")
                .data("{\"id\":1}".getBytes())
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Process
        int processed = eventProcessor.process("test-processor");

        // Then - Should return 0 (skipped)
        assertThat(processed).isEqualTo(0);
        assertThat(eventHandler.getHandledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle empty event batches")
    void shouldHandleEmptyEventBatches() {
        // Given - No events in store

        // When - Process
        int processed = eventProcessor.process("test-processor");

        // Then - Should return 0
        assertThat(processed).isEqualTo(0);
        assertThat(eventHandler.getHandledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should resume from last position")
    void shouldResumeFromLastPosition() {
        // Given - Process some events first
        List<AppendEvent> firstBatch = List.of(
            AppendEvent.builder("Event1")
                .data("{\"id\":1}".getBytes())
                .build(),
            AppendEvent.builder("Event2")
                .data("{\"id\":2}".getBytes())
                .build()
        );
        eventStore.appendIf(firstBatch, AppendCondition.empty());
        eventProcessor.process("test-processor");
        
        int initialHandled = eventHandler.getHandledCount();
        assertThat(initialHandled).isEqualTo(2);

        // When - Add more events and process again
        List<AppendEvent> secondBatch = List.of(
            AppendEvent.builder("Event3")
                .data("{\"id\":3}".getBytes())
                .build()
        );
        eventStore.appendIf(secondBatch, AppendCondition.empty());
        int processed = eventProcessor.process("test-processor");

        // Then - Should process only new events
        assertThat(processed).isEqualTo(1);
        assertThat(eventHandler.getHandledCount()).isEqualTo(3);
        assertThat(progressTracker.getLastPosition("test-processor")).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should handle errors and record error count")
    void shouldHandleErrors_AndRecordErrorCount() {
        // Given - Handler that throws exception
        eventHandler.setShouldThrow(true);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent")
                .data("{\"id\":1}".getBytes())
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Process (should throw)
        assertThatThrownBy(() -> eventProcessor.process("test-processor"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to handle events");

        // Then - Error should be recorded
        assertThat(progressTracker.getErrorCount("test-processor")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reset error count on successful processing")
    void shouldResetErrorCount_OnSuccessfulProcessing() {
        // Given - Processor with errors
        progressTracker.recordError("test-processor", "Test error", 10);
        assertThat(progressTracker.getErrorCount("test-processor")).isGreaterThan(0);
        
        eventHandler.setShouldThrow(false);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent")
                .data("{\"id\":1}".getBytes())
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Process successfully
        int processed = eventProcessor.process("test-processor");

        // Then - Error count should be reset
        assertThat(processed).isEqualTo(1);
        assertThat(progressTracker.getErrorCount("test-processor")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should respect batch size limit")
    void shouldRespectBatchSizeLimit() {
        // Given - More events than batch size
        List<AppendEvent> events = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            events.add(AppendEvent.builder("Event" + i)
                .data(("{\"id\":" + i + "}").getBytes())
                .build());
        }
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Process with batch size 5
        int processed = eventProcessor.process("test-processor");

        // Then - Should process only batch size
        assertThat(processed).isEqualTo(5); // Batch size is 5
        assertThat(eventHandler.getHandledCount()).isEqualTo(5);
        
        // Process again to get remaining events
        int secondProcessed = eventProcessor.process("test-processor");
        assertThat(secondProcessed).isEqualTo(5);
        assertThat(eventHandler.getHandledCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should throw exception for non-existent processor")
    void shouldThrowException_ForNonExistentProcessor() {
        // When & Then
        assertThatThrownBy(() -> eventProcessor.process("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Processor not found");
    }

    @Test
    @DisplayName("Should get all processor statuses")
    void shouldGetAllProcessorStatuses() {
        // Given - Multiple processors configured
        eventProcessor.process("test-processor");
        eventProcessor.process("test-processor-2");

        // When
        Map<String, ProcessorStatus> statuses = eventProcessor.getAllStatuses();

        // Then
        assertThat(statuses).containsKey("test-processor");
        assertThat(statuses).containsKey("test-processor-2");
        assertThat(statuses.get("test-processor")).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should pause and resume processor")
    void shouldPauseAndResumeProcessor() {
        // Given - Processor is active
        eventProcessor.process("test-processor");
        assertThat(eventProcessor.getStatus("test-processor")).isEqualTo(ProcessorStatus.ACTIVE);

        // When - Pause
        eventProcessor.pause("test-processor");

        // Then
        assertThat(eventProcessor.getStatus("test-processor")).isEqualTo(ProcessorStatus.PAUSED);

        // When - Resume
        eventProcessor.resume("test-processor");

        // Then
        assertThat(eventProcessor.getStatus("test-processor")).isEqualTo(ProcessorStatus.ACTIVE);
    }

    // Test implementations

    static class TestInstanceIdProvider {
        public String getInstanceId() {
            return "test-instance";
        }
    }

    static class TestProcessorConfig implements ProcessorConfig<String> {
        private final String processorId;
        private final int batchSize;

        TestProcessorConfig(String processorId, int batchSize) {
            this.processorId = processorId;
            this.batchSize = batchSize;
        }

        @Override
        public String getProcessorId() {
            return processorId;
        }

        @Override
        public long getPollingIntervalMs() {
            return 1000L;
        }

        @Override
        public int getBatchSize() {
            return batchSize;
        }

        @Override
        public boolean isBackoffEnabled() {
            return false;
        }

        @Override
        public int getBackoffThreshold() {
            return 0;
        }

        @Override
        public int getBackoffMultiplier() {
            return 0;
        }

        @Override
        public int getBackoffMaxSeconds() {
            return 0;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    static class TestProgressTracker implements ProgressTracker<String> {
        private final DataSource dataSource;
        private final Map<String, Long> positions = new HashMap<>();
        private final Map<String, ProcessorStatus> statuses = new HashMap<>();
        private final Map<String, Integer> errorCounts = new HashMap<>();

        TestProgressTracker(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public long getLastPosition(String processorId) {
            return positions.getOrDefault(processorId, 0L);
        }

        @Override
        public void updateProgress(String processorId, long position) {
            positions.put(processorId, position);
            statuses.put(processorId, ProcessorStatus.ACTIVE);
            errorCounts.put(processorId, 0);
        }

        @Override
        public void recordError(String processorId, String error, int maxErrors) {
            errorCounts.put(processorId, errorCounts.getOrDefault(processorId, 0) + 1);
            if (errorCounts.get(processorId) >= maxErrors) {
                statuses.put(processorId, ProcessorStatus.FAILED);
            }
        }

        @Override
        public void resetErrorCount(String processorId) {
            errorCounts.put(processorId, 0);
        }

        @Override
        public ProcessorStatus getStatus(String processorId) {
            return statuses.getOrDefault(processorId, ProcessorStatus.ACTIVE);
        }

        @Override
        public void setStatus(String processorId, ProcessorStatus status) {
            statuses.put(processorId, status);
        }

        @Override
        public void autoRegister(String processorId, String instanceId) {
            if (!positions.containsKey(processorId)) {
                positions.put(processorId, 0L);
                statuses.put(processorId, ProcessorStatus.ACTIVE);
                errorCounts.put(processorId, 0);
            }
        }

        public int getErrorCount(String processorId) {
            return errorCounts.getOrDefault(processorId, 0);
        }
    }

    static class TestEventHandler implements EventHandler<String> {
        private final AtomicInteger handledCount = new AtomicInteger(0);
        private volatile boolean shouldThrow = false;

        @Override
        public int handle(String processorId, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
            if (shouldThrow) {
                throw new RuntimeException("Test exception");
            }
            handledCount.addAndGet(events.size());
            return events.size();
        }

        public int getHandledCount() {
            return handledCount.get();
        }

        public void reset() {
            handledCount.set(0);
            shouldThrow = false;
        }

        public void setShouldThrow(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }
    }

    static class TestEventFetcher implements EventFetcher<String> {
        private final DataSource readDataSource;

        TestEventFetcher(DataSource readDataSource) {
            this.readDataSource = readDataSource;
        }

        @Override
        public List<StoredEvent> fetchEvents(String processorId, long lastPosition, int batchSize) {
            // Simple implementation: fetch events directly from database
            // In real implementation, this would use read replica and filter by tags/types
            try (var connection = readDataSource.getConnection();
                 var stmt = connection.prepareStatement(
                     "SELECT type, tags, data, transaction_id, position, occurred_at " +
                     "FROM events WHERE position > ? ORDER BY position ASC LIMIT ?")) {
                stmt.setLong(1, lastPosition);
                stmt.setInt(2, batchSize);
                
                List<StoredEvent> events = new ArrayList<>();
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        events.add(new StoredEvent(
                            rs.getString("type"),
                            parseTags(rs.getArray("tags")),
                            rs.getBytes("data"),
                            rs.getString("transaction_id"),
                            rs.getLong("position"),
                            rs.getTimestamp("occurred_at").toInstant()
                        ));
                    }
                }
                return events;
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch events", e);
            }
        }

        private List<com.crablet.eventstore.store.Tag> parseTags(java.sql.Array array) throws java.sql.SQLException {
            if (array == null) {
                return List.of();
            }
            String[] tagStrings = (String[]) array.getArray();
            List<com.crablet.eventstore.store.Tag> tags = new ArrayList<>();
            for (String tagStr : tagStrings) {
                int equalsIndex = tagStr.indexOf('=');
                if (equalsIndex > 0) {
                    String key = tagStr.substring(0, equalsIndex);
                    String value = tagStr.substring(equalsIndex + 1);
                    tags.add(new com.crablet.eventstore.store.Tag(key, value));
                }
            }
            return tags;
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(AbstractEventProcessorTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractEventProcessorTest.postgres.getUsername());
            dataSource.setPassword(AbstractEventProcessorTest.postgres.getPassword());
            return dataSource;
        }

        @Bean
        @Primary
        public DataSource primaryDataSource(DataSource dataSource) {
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

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public EventStore eventStore(
                DataSource dataSource,
                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                com.crablet.eventstore.store.EventStoreConfig config,
                com.crablet.eventstore.clock.ClockProvider clock,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new com.crablet.eventstore.store.EventStoreImpl(
                dataSource, dataSource, objectMapper, config, clock, eventPublisher);
        }

        @Bean
        public com.crablet.eventstore.store.EventStoreConfig eventStoreConfig() {
            return new com.crablet.eventstore.store.EventStoreConfig();
        }

        @Bean
        public com.crablet.eventstore.clock.ClockProvider clockProvider() {
            return new com.crablet.eventstore.clock.ClockProviderImpl();
        }

        @Bean
        public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        @Bean
        public TestInstanceIdProvider instanceIdProvider() {
            return new TestInstanceIdProvider();
        }

        @Bean
        public TaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }

        @Bean
        public org.springframework.context.ApplicationEventPublisher applicationEventPublisher() {
            return new org.springframework.context.support.GenericApplicationContext();
        }

        @Bean
        public TestProgressTracker testProgressTracker(DataSource dataSource) {
            return new TestProgressTracker(dataSource);
        }

        @Bean
        public ProgressTracker<String> progressTracker(TestProgressTracker testProgressTracker) {
            return testProgressTracker;
        }

        @Bean
        public TestEventHandler testEventHandler() {
            return new TestEventHandler();
        }

        @Bean
        public EventHandler<String> eventHandler(TestEventHandler testEventHandler) {
            return testEventHandler;
        }

        @Bean
        public EventFetcher<String> eventFetcher(DataSource dataSource) {
            return new TestEventFetcher(dataSource);
        }

        @Bean
        public LeaderElector leaderElector(
                DataSource dataSource,
                TestInstanceIdProvider instanceIdProvider,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new LeaderElectorImpl(dataSource, instanceIdProvider.getInstanceId(), 999999L, eventPublisher);
        }

        @Bean
        public Map<String, TestProcessorConfig> processorConfigs() {
            Map<String, TestProcessorConfig> configs = new HashMap<>();
            configs.put("test-processor", new TestProcessorConfig("test-processor", 5));
            configs.put("test-processor-2", new TestProcessorConfig("test-processor-2", 5));
            return configs;
        }

        @Bean
        public EventProcessor<TestProcessorConfig, String> eventProcessor(
                Map<String, TestProcessorConfig> processorConfigs,
                LeaderElector leaderElector,
                ProgressTracker<String> progressTracker,
                EventFetcher<String> eventFetcher,
                EventHandler<String> eventHandler,
                DataSource writeDataSource,
                TaskScheduler taskScheduler,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new EventProcessorImpl<>(
                processorConfigs,
                leaderElector,
                progressTracker,
                eventFetcher,
                eventHandler,
                writeDataSource,
                taskScheduler,
                eventPublisher
            );
        }
    }
}

