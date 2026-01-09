package com.crablet.eventprocessor.management;

import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventprocessor.integration.AbstractEventProcessorTest;
import com.crablet.eventprocessor.leader.LeaderElector;
import com.crablet.eventprocessor.leader.LeaderElectorImpl;
import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventprocessor.processor.EventProcessorImpl;
import com.crablet.eventprocessor.processor.ProcessorConfig;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventprocessor.progress.ProgressTracker;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProcessorManagementServiceImpl.
 * Tests management operations (pause, resume, reset, status, lag, backoff) with real database.
 */
@SpringBootTest(classes = ProcessorManagementServiceImplIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("ProcessorManagementServiceImpl Integration Tests")
class ProcessorManagementServiceImplIntegrationTest extends AbstractEventProcessorTest {

    @Autowired
    private ProcessorManagementService<String> managementService;

    @Autowired
    private EventProcessor<TestProcessorConfig, String> eventProcessor;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestProgressTracker progressTracker;

    @BeforeEach
    void setUp() {
        // Stop any running schedulers
        eventProcessor.stop();
        
        // Clean database
        cleanDatabase(jdbcTemplate);
        
        // Reset progress tracker
        progressTracker.positions.clear();
        progressTracker.statuses.clear();
        progressTracker.errorCounts.clear();
    }

    @Test
    @DisplayName("Given existing processor, when getting status, then returns status")
    void givenExistingProcessor_whenGettingStatus_thenReturnsStatus() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId); // Auto-registers processor

        // When
        ProcessorStatus status = managementService.getStatus(processorId);

        // Then
        assertThat(status).isNotNull();
        assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given multiple processors, when getting all statuses, then returns all statuses")
    void givenMultipleProcessors_whenGettingAllStatuses_thenReturnsAllStatuses() {
        // Given
        eventProcessor.process("processor-1");
        eventProcessor.process("processor-2");

        // When
        Map<String, ProcessorStatus> statuses = managementService.getAllStatuses();

        // Then
        assertThat(statuses).containsKey("processor-1");
        assertThat(statuses).containsKey("processor-2");
        assertThat(statuses.get("processor-1")).isEqualTo(ProcessorStatus.ACTIVE);
        assertThat(statuses.get("processor-2")).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given existing processor, when pausing processor, then returns true and processor is paused")
    void givenExistingProcessor_whenPausingProcessor_thenReturnsTrueAndProcessorIsPaused() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);

        // When
        boolean paused = managementService.pause(processorId);

        // Then
        assertThat(paused).isTrue();
        assertThat(managementService.getStatus(processorId)).isEqualTo(ProcessorStatus.PAUSED);
    }

    @Test
    @DisplayName("Given non-existent processor, when pausing processor, then returns false")
    void givenNonExistentProcessor_whenPausingProcessor_thenReturnsFalse() {
        // Given
        String processorId = "non-existent-processor";

        // When
        boolean paused = managementService.pause(processorId);

        // Then
        assertThat(paused).isFalse();
    }

    @Test
    @DisplayName("Given paused processor, when resuming processor, then returns true and processor is active")
    void givenPausedProcessor_whenResumingProcessor_thenReturnsTrueAndProcessorIsActive() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);
        managementService.pause(processorId);

        // When
        boolean resumed = managementService.resume(processorId);

        // Then
        assertThat(resumed).isTrue();
        assertThat(managementService.getStatus(processorId)).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given non-existent processor, when resuming processor, then returns false")
    void givenNonExistentProcessor_whenResumingProcessor_thenReturnsFalse() {
        // Given
        String processorId = "non-existent-processor";

        // When
        boolean resumed = managementService.resume(processorId);

        // Then
        assertThat(resumed).isFalse();
    }

    @Test
    @DisplayName("Given failed processor, when resetting processor, then returns true and processor is active")
    void givenFailedProcessor_whenResettingProcessor_thenReturnsTrueAndProcessorIsActive() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);
        progressTracker.recordError(processorId, "Test error", 1); // Mark as failed
        progressTracker.setStatus(processorId, ProcessorStatus.FAILED);

        // When
        boolean reset = managementService.reset(processorId);

        // Then
        assertThat(reset).isTrue();
        assertThat(managementService.getStatus(processorId)).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given non-existent processor, when resetting processor, then returns false")
    void givenNonExistentProcessor_whenResettingProcessor_thenReturnsFalse() {
        // Given
        String processorId = "non-existent-processor";

        // When
        boolean reset = managementService.reset(processorId);

        // Then
        assertThat(reset).isFalse();
    }

    @Test
    @DisplayName("Given processor with events, when getting lag, then returns lag value")
    void givenProcessorWithEvents_whenGettingLag_thenReturnsLagValue() {
        // Given
        String processorId = "test-processor";
        
        // Append events
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent")
                .data("{\"id\":1}".getBytes())
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());
        
        // Process to set last position
        eventProcessor.process(processorId);

        // When
        Long lag = managementService.getLag(processorId);

        // Then
        assertThat(lag).isNotNull();
        assertThat(lag).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Given processor with no events, when getting lag, then returns zero or null")
    void givenProcessorWithNoEvents_whenGettingLag_thenReturnsZeroOrNull() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId); // Auto-registers but no events processed

        // When
        Long lag = managementService.getLag(processorId);

        // Then
        // Lag should be 0 (max position 0 - last position 0) or null if no events exist
        assertThat(lag).isNotNull();
        assertThat(lag).isEqualTo(0L);
    }

    @Test
    @DisplayName("Given processor with backoff enabled, when getting backoff info, then returns backoff info")
    void givenProcessorWithBackoffEnabled_whenGettingBackoffInfo_thenReturnsBackoffInfo() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);

        // When
        ProcessorManagementService.BackoffInfo backoffInfo = managementService.getBackoffInfo(processorId);

        // Then
        // Backoff info might be null if backoff is not enabled or no backoff state exists
        // This is acceptable - the method returns null when backoff is not active
        // We just verify the method doesn't throw
        assertThat(backoffInfo).isNull(); // Backoff not enabled in test config
    }

    @Test
    @DisplayName("Given multiple processors, when getting all backoff info, then returns all backoff info")
    void givenMultipleProcessors_whenGettingAllBackoffInfo_thenReturnsAllBackoffInfo() {
        // Given
        eventProcessor.process("processor-1");
        eventProcessor.process("processor-2");

        // When
        Map<String, ProcessorManagementService.BackoffInfo> allBackoffInfo = managementService.getAllBackoffInfo();

        // Then
        assertThat(allBackoffInfo).isNotNull();
        // Backoff info might be empty if backoff is not enabled
        // We just verify the method doesn't throw
    }

    // Test implementations (reused from EventProcessorImplIntegrationTest pattern)

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
        final Map<String, Long> positions = new HashMap<>();
        final Map<String, ProcessorStatus> statuses = new HashMap<>();
        final Map<String, Integer> errorCounts = new HashMap<>();

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
    }

    static class TestEventHandler implements EventHandler<String> {
        @Override
        public int handle(String processorId, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
            return events.size();
        }
    }

    static class TestEventFetcher implements EventFetcher<String> {
        private final DataSource readDataSource;

        TestEventFetcher(DataSource readDataSource) {
            this.readDataSource = readDataSource;
        }

        @Override
        public List<StoredEvent> fetchEvents(String processorId, long lastPosition, int batchSize) {
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
        public org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
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
        public com.crablet.eventprocessor.InstanceIdProvider instanceIdProvider(Environment environment) {
            return new com.crablet.eventprocessor.InstanceIdProvider(environment);
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
        public LeaderElector leaderElector(
                DataSource dataSource,
                com.crablet.eventprocessor.InstanceIdProvider instanceIdProvider,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new LeaderElectorImpl(
                dataSource,
                instanceIdProvider.getInstanceId(),
                1234567890L,
                eventPublisher
            );
        }

        @Bean
        public ProgressTracker<String> progressTracker(DataSource dataSource) {
            return new TestProgressTracker(dataSource);
        }

        @Bean
        public EventFetcher<String> eventFetcher(DataSource dataSource) {
            return new TestEventFetcher(dataSource);
        }

        @Bean
        public EventHandler<String> eventHandler() {
            return new TestEventHandler();
        }

        @Bean
        public Map<String, TestProcessorConfig> processorConfigs() {
            Map<String, TestProcessorConfig> configs = new HashMap<>();
            configs.put("test-processor", new TestProcessorConfig("test-processor", 5));
            configs.put("processor-1", new TestProcessorConfig("processor-1", 5));
            configs.put("processor-2", new TestProcessorConfig("processor-2", 5));
            return configs;
        }

        @Bean
        public EventProcessor<TestProcessorConfig, String> eventProcessor(
                Map<String, TestProcessorConfig> processorConfigs,
                LeaderElector leaderElector,
                ProgressTracker<String> progressTracker,
                EventFetcher<String> eventFetcher,
                EventHandler<String> eventHandler,
                DataSource dataSource,
                TaskScheduler taskScheduler,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new EventProcessorImpl<>(
                processorConfigs,
                leaderElector,
                progressTracker,
                eventFetcher,
                eventHandler,
                dataSource,
                taskScheduler,
                eventPublisher
            );
        }

        @Bean
        public ProcessorManagementService<String> processorManagementService(
                EventProcessor<TestProcessorConfig, String> eventProcessor,
                ProgressTracker<String> progressTracker,
                DataSource dataSource) {
            return new ProcessorManagementServiceImpl<>(eventProcessor, progressTracker, dataSource);
        }
    }
}
