package com.crablet.eventprocessor.management;

import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventprocessor.integration.AbstractEventProcessorTest;
import com.crablet.eventprocessor.leader.LeaderElector;
import com.crablet.eventprocessor.leader.LeaderElectorImpl;
import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventprocessor.processor.EventProcessorImpl;
import com.crablet.eventprocessor.processor.ProcessorConfig;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E2E tests for ProcessorManagementController REST endpoints.
 * Tests all HTTP endpoints using WebTestClient with full Spring Boot context.
 */
@SpringBootTest(
    classes = ProcessorManagementControllerE2ETest.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("ProcessorManagementController E2E Tests")
class ProcessorManagementControllerE2ETest extends AbstractEventProcessorTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventProcessor<TestProcessorConfig, String> eventProcessor;

    @Autowired
    private TestProgressTracker testProgressTracker;

    @Autowired
    private EventStore eventStore;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // Stop any running schedulers
        eventProcessor.stop();
        
        // Clean database
        cleanDatabase(jdbcTemplate);
        
        // Reset progress tracker
        testProgressTracker.positions.clear();
        testProgressTracker.statuses.clear();
        testProgressTracker.errorCounts.clear();
        
        // Configure WebTestClient
        webTestClient = WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    @Test
    @DisplayName("Given existing processors, when getting all statuses, then returns all statuses")
    void givenExistingProcessors_whenGettingAllStatuses_thenReturnsAllStatuses() {
        // Given
        eventProcessor.process("processor-1");
        eventProcessor.process("processor-2");

        // When & Then
        webTestClient.get()
            .uri("/api/processors")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.processor-1").exists()
            .jsonPath("$.processor-2").exists();
    }

    @Test
    @DisplayName("Given existing processor, when getting status, then returns status")
    void givenExistingProcessor_whenGettingStatus_thenReturnsStatus() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);

        // When & Then
        webTestClient.get()
            .uri("/api/processors/{id}", processorId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ProcessorStatus.class)
            .value(status -> {
                assert status != null;
                assert status == ProcessorStatus.ACTIVE;
            });
    }

    @Test
    @DisplayName("Given non-existent processor, when getting status, then returns ACTIVE status")
    void givenNonExistentProcessor_whenGettingStatus_thenReturnsActiveStatus() {
        // Given
        String processorId = "non-existent-processor";

        // When & Then - getStatus() returns ACTIVE for non-existent processors (default behavior)
        webTestClient.get()
            .uri("/api/processors/{id}", processorId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ProcessorStatus.class)
            .value(status -> {
                assert status != null;
                assert status == ProcessorStatus.ACTIVE;
            });
    }

    @Test
    @DisplayName("Given existing processor, when pausing processor, then returns 200")
    void givenExistingProcessor_whenPausingProcessor_thenReturns200() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);

        // When & Then
        webTestClient.post()
            .uri("/api/processors/{id}/pause", processorId)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("Given non-existent processor, when pausing processor, then returns 404")
    void givenNonExistentProcessor_whenPausingProcessor_thenReturns404() {
        // Given
        String processorId = "non-existent-processor";

        // When & Then
        webTestClient.post()
            .uri("/api/processors/{id}/pause", processorId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Given paused processor, when resuming processor, then returns 200")
    void givenPausedProcessor_whenResumingProcessor_thenReturns200() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);
        eventProcessor.pause(processorId);

        // When & Then
        webTestClient.post()
            .uri("/api/processors/{id}/resume", processorId)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("Given non-existent processor, when resuming processor, then returns 404")
    void givenNonExistentProcessor_whenResumingProcessor_thenReturns404() {
        // Given
        String processorId = "non-existent-processor";

        // When & Then
        webTestClient.post()
            .uri("/api/processors/{id}/resume", processorId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Given existing processor, when resetting processor, then returns 200")
    void givenExistingProcessor_whenResettingProcessor_thenReturns200() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);

        // When & Then
        webTestClient.post()
            .uri("/api/processors/{id}/reset", processorId)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("Given non-existent processor, when resetting processor, then returns 404")
    void givenNonExistentProcessor_whenResettingProcessor_thenReturns404() {
        // Given
        String processorId = "non-existent-processor";

        // When & Then
        webTestClient.post()
            .uri("/api/processors/{id}/reset", processorId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Given existing processor with events, when getting lag, then returns lag value")
    void givenExistingProcessorWithEvents_whenGettingLag_thenReturnsLagValue() {
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

        // When & Then
        webTestClient.get()
            .uri("/api/processors/{id}/lag", processorId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .value(lag -> {
                assert lag != null;
                assert lag >= 0L;
            });
    }

    @Test
    @DisplayName("Given non-existent processor, when getting lag, then returns zero lag")
    void givenNonExistentProcessor_whenGettingLag_thenReturnsZeroLag() {
        // Given
        String processorId = "non-existent-processor";

        // When & Then - getLag() returns 0 for non-existent processors (max position 0 - last position 0 = 0)
        webTestClient.get()
            .uri("/api/processors/{id}/lag", processorId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .value(lag -> {
                assert lag != null;
                assert lag == 0L;
            });
    }

    @Test
    @DisplayName("Given existing processor, when getting backoff info, then returns backoff info or 404")
    void givenExistingProcessor_whenGettingBackoffInfo_thenReturnsBackoffInfoOr404() {
        // Given
        String processorId = "test-processor";
        eventProcessor.process(processorId);

        // When & Then
        // Backoff info might be null if backoff is not enabled, which returns 404
        webTestClient.get()
            .uri("/api/processors/{id}/backoff", processorId)
            .exchange()
            .expectStatus().isNotFound(); // Backoff not enabled in test config
    }

    @Test
    @DisplayName("Given multiple processors, when getting all backoff info, then returns all backoff info")
    void givenMultipleProcessors_whenGettingAllBackoffInfo_thenReturnsAllBackoffInfo() {
        // Given
        eventProcessor.process("processor-1");
        eventProcessor.process("processor-2");

        // When & Then
        webTestClient.get()
            .uri("/api/processors/backoff")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isMap();
    }

    // Test implementations (reused from ProcessorManagementServiceImplIntegrationTest)

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

    @org.springframework.boot.autoconfigure.SpringBootApplication
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

        @Bean
        public com.crablet.eventprocessor.management.ProcessorManagementController processorManagementController(
                ProcessorManagementService<String> processorManagementService) {
            return new com.crablet.eventprocessor.management.ProcessorManagementController(processorManagementService);
        }
    }
}
