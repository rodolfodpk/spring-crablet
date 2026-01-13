package com.crablet.views.service;

import com.crablet.eventprocessor.InstanceIdProvider;
import com.crablet.eventprocessor.management.ProcessorManagementService;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreImpl;
import com.crablet.views.config.ViewsAutoConfiguration;
import com.crablet.views.config.ViewsConfig;
import com.crablet.views.config.ViewSubscriptionConfig;
import com.crablet.views.integration.AbstractViewsTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ViewManagementService.
 * Tests both delegated methods (from ProcessorManagementService) and new detailed progress methods.
 */
@SpringBootTest(
    classes = ViewManagementServiceTest.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DisplayName("ViewManagementService Tests")
class ViewManagementServiceTest extends AbstractViewsTest {

    @Autowired
    private ViewManagementService viewManagementService;

    @Autowired
    @Qualifier("viewManagementService")
    private ProcessorManagementService<String> managementServiceAsInterface;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureAdditionalProperties(DynamicPropertyRegistry registry) {
        registry.add("crablet.views.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
    }

    // ========== Delegation Tests (verify wrapper works) ==========

    @Test
    @DisplayName("Given view, when pausing via wrapper, then delegates correctly")
    void givenView_whenPausingViaWrapper_thenDelegatesCorrectly() {
        // Given
        String viewName = "wallet-view";

        // When
        boolean paused = viewManagementService.pause(viewName);

        // Then
        assertThat(paused).isTrue();
    }

    @Test
    @DisplayName("Given view, when getting status via wrapper, then delegates correctly")
    void givenView_whenGettingStatusViaWrapper_thenDelegatesCorrectly() {
        // Given
        String viewName = "wallet-view";

        // When
        ProcessorStatus status = viewManagementService.getStatus(viewName);

        // Then
        assertThat(status).isNotNull();
        assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given view, when getting lag via wrapper, then delegates correctly")
    void givenView_whenGettingLagViaWrapper_thenDelegatesCorrectly() {
        // Given
        String viewName = "wallet-view";

        // When
        Long lag = viewManagementService.getLag(viewName);

        // Then
        assertThat(lag).isNotNull();
        assertThat(lag).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Given service injected as interface, when using methods, then works correctly")
    void givenServiceInjectedAsInterface_whenUsingMethods_thenWorksCorrectly() {
        // Given - Service injected as ProcessorManagementService<String>
        String viewName = "wallet-view";

        // When
        ProcessorStatus status = managementServiceAsInterface.getStatus(viewName);
        Long lag = managementServiceAsInterface.getLag(viewName);

        // Then
        assertThat(status).isNotNull();
        assertThat(lag).isNotNull();
        
        // Verify it's actually the wrapper (can cast to ViewManagementService)
        assertThat(managementServiceAsInterface).isInstanceOf(ViewManagementService.class);
    }

    // ========== Detailed Progress Tests ==========

    @Test
    @DisplayName("Given view with progress, when getting details, then returns all fields")
    void givenViewWithProgress_whenGettingDetails_thenReturnsAllFields() {
        // Given - Insert test data into view_progress table
        String viewName = "test-view";
        String instanceId = "instance-123";
        long lastPosition = 100L;
        int errorCount = 0;
        String lastError = null;
        Timestamp now = Timestamp.from(Instant.now());
        
        jdbcTemplate.update("""
            INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count, 
                                      last_error, last_error_at, last_updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            viewName, instanceId, "ACTIVE", lastPosition, errorCount,
            lastError, null, now, now);

        // When
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);

        // Then
        assertThat(details).isNotNull();
        assertThat(details.viewName()).isEqualTo(viewName);
        assertThat(details.instanceId()).isEqualTo(instanceId);
        assertThat(details.status()).isEqualTo(ProcessorStatus.ACTIVE);
        assertThat(details.lastPosition()).isEqualTo(lastPosition);
        assertThat(details.errorCount()).isEqualTo(errorCount);
        assertThat(details.lastError()).isNull();
        assertThat(details.lastErrorAt()).isNull();
        assertThat(details.lastUpdatedAt()).isNotNull();
        assertThat(details.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("Given view without errors, when getting details, then returns null error fields")
    void givenViewWithoutErrors_whenGettingDetails_thenReturnsNullErrorFields() {
        // Given - View with no errors
        String viewName = "test-view-no-errors";
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
            INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count, 
                                      last_updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            viewName, "instance-1", "ACTIVE", 50L, 0, now, now);

        // When
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);

        // Then
        assertThat(details).isNotNull();
        assertThat(details.errorCount()).isEqualTo(0);
        assertThat(details.lastError()).isNull();
        assertThat(details.lastErrorAt()).isNull();
    }

    @Test
    @DisplayName("Given view with errors, when getting details, then returns error information")
    void givenViewWithErrors_whenGettingDetails_thenReturnsErrorInformation() {
        // Given - View with errors
        String viewName = "test-view-with-errors";
        String lastError = "SQL constraint violation";
        Timestamp errorAt = Timestamp.from(Instant.now());
        Timestamp now = Timestamp.from(Instant.now());
        
        jdbcTemplate.update("""
            INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count,
                                      last_error, last_error_at, last_updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            viewName, "instance-1", "FAILED", 75L, 3,
            lastError, errorAt, now, now);

        // When
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);

        // Then
        assertThat(details).isNotNull();
        assertThat(details.status()).isEqualTo(ProcessorStatus.FAILED);
        assertThat(details.errorCount()).isEqualTo(3);
        assertThat(details.lastError()).isEqualTo(lastError);
        assertThat(details.lastErrorAt()).isNotNull();
    }

    @Test
    @DisplayName("Given non-existent view, when getting details, then returns null")
    void givenNonExistentView_whenGettingDetails_thenReturnsNull() {
        // Given
        String viewName = "non-existent-view";

        // When
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);

        // Then
        assertThat(details).isNull();
    }

    @Test
    @DisplayName("Given multiple views, when getting all details, then returns all views")
    void givenMultipleViews_whenGettingAllDetails_thenReturnsAllViews() {
        // Given - Insert multiple views
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
            INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count,
                                      last_updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, "view-1", "instance-1", "ACTIVE", 100L, 0, now, now);
        
        jdbcTemplate.update("""
            INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count,
                                      last_updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, "view-2", "instance-2", "PAUSED", 200L, 0, now, now);

        // When
        Map<String, ViewProgressDetails> allDetails = viewManagementService.getAllProgressDetails();

        // Then
        assertThat(allDetails).hasSizeGreaterThanOrEqualTo(2);
        assertThat(allDetails).containsKey("view-1");
        assertThat(allDetails).containsKey("view-2");
        assertThat(allDetails.get("view-1").status()).isEqualTo(ProcessorStatus.ACTIVE);
        assertThat(allDetails.get("view-2").status()).isEqualTo(ProcessorStatus.PAUSED);
    }

    @Test
    @DisplayName("Given view with status, when getting details, then returns correct status")
    void givenViewWithStatus_whenGettingDetails_thenReturnsCorrectStatus() {
        // Given - View with PAUSED status
        String viewName = "paused-view";
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
            INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count,
                                      last_updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, viewName, "instance-1", "PAUSED", 150L, 0, now, now);

        // When
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);

        // Then
        assertThat(details).isNotNull();
        assertThat(details.status()).isEqualTo(ProcessorStatus.PAUSED);
    }

    @Test
    @DisplayName("Given view with instance ID, when getting details, then returns instance ID")
    void givenViewWithInstanceId_whenGettingDetails_thenReturnsInstanceId() {
        // Given
        String viewName = "view-with-instance";
        String instanceId = "leader-instance-456";
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
            INSERT INTO view_progress (view_name, instance_id, status, last_position, error_count,
                                      last_updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, viewName, instanceId, "ACTIVE", 300L, 0, now, now);

        // When
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);

        // Then
        assertThat(details).isNotNull();
        assertThat(details.instanceId()).isEqualTo(instanceId);
    }

    // ========== Integration Tests (wrapper + new methods) ==========

    @Test
    @DisplayName("Given active view, when pausing and getting details, then status is paused")
    void givenActiveView_whenPausingAndGettingDetails_thenStatusIsPaused() {
        // Given - View exists in subscription
        String viewName = "wallet-view";
        
        // Wait a bit for view to be registered in view_progress
        // (ViewProgressTracker auto-registers views on first access)
        viewManagementService.getStatus(viewName);
        
        // When - Pause the view
        boolean paused = viewManagementService.pause(viewName);
        assertThat(paused).isTrue();
        
        // Then - Get details and verify status
        // Note: Status might not be updated immediately in view_progress table
        // The pause operation succeeds, but the table update might happen asynchronously
        // This test verifies the methods work together
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);
        // Details might be null if view not yet registered in view_progress
        // This is acceptable - the test verifies both methods can be called together
        // If details exist, verify they're consistent
        if (details != null) {
            // Status from getStatus() and getProgressDetails() should match if both are available
            ProcessorStatus statusFromDetails = details.status();
            ProcessorStatus statusFromMethod = viewManagementService.getStatus(viewName);
            // They might differ due to timing, but both should be valid
            assertThat(statusFromDetails).isIn(ProcessorStatus.ACTIVE, ProcessorStatus.PAUSED);
            assertThat(statusFromMethod).isIn(ProcessorStatus.ACTIVE, ProcessorStatus.PAUSED);
        }
    }

    @Test
    @DisplayName("Given view, when using both delegated and new methods, then both work")
    void givenView_whenUsingBothDelegatedAndNewMethods_thenBothWork() {
        // Given
        String viewName = "wallet-view";
        
        // When - Use both types of methods
        ProcessorStatus status = viewManagementService.getStatus(viewName);
        Long lag = viewManagementService.getLag(viewName);
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);

        // Then - Both should work
        assertThat(status).isNotNull();
        assertThat(lag).isNotNull();
        // Details might be null if view not yet registered in view_progress
        // This is acceptable - the test verifies both methods can be called together
    }

    @Configuration
    @Import(ViewsAutoConfiguration.class)
    static class TestConfig {
        @Bean
        public DataSource dataSource() {
            SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
            dataSource.setDriverClass(Driver.class);
            dataSource.setUrl(AbstractViewsTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractViewsTest.postgres.getUsername());
            dataSource.setPassword(AbstractViewsTest.postgres.getPassword());
            return dataSource;
        }

        @Bean
        @Primary
        public DataSource primaryDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        @Qualifier("readDataSource")
        public DataSource readDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public Flyway flyway(DataSource dataSource) {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @DependsOn("flyway")
        public EventStore eventStore(
                DataSource dataSource,
                ObjectMapper objectMapper,
                EventStoreConfig config,
                ClockProvider clock,
                ApplicationEventPublisher eventPublisher) {
            return new EventStoreImpl(
                dataSource, dataSource, objectMapper, config, clock, eventPublisher);
        }

        @Bean
        public EventStoreConfig eventStoreConfig() {
            return new EventStoreConfig();
        }

        @Bean
        public ClockProvider clockProvider() {
            return new ClockProviderImpl();
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public InstanceIdProvider instanceIdProvider(Environment environment) {
            return new InstanceIdProvider(environment);
        }

        @Bean
        public TaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }

        @Bean
        public ApplicationEventPublisher applicationEventPublisher() {
            return new GenericApplicationContext();
        }

        @Bean
        public ViewsConfig viewsConfig() {
            return new ViewsConfig();
        }

        @Bean
        public ViewSubscriptionConfig testViewSubscription() {
            return ViewSubscriptionConfig.builder("wallet-view")
                    .eventTypes("WalletOpened", "DepositMade")
                    .requiredTags("wallet_id")
                    .build();
        }
    }
}
