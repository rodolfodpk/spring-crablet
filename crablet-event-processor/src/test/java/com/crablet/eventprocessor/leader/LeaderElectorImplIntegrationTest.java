package com.crablet.eventprocessor.leader;

import com.crablet.eventprocessor.integration.AbstractEventProcessorTest;
import com.crablet.eventprocessor.metrics.LeadershipMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for LeaderElectorImpl.
 * Tests PostgreSQL advisory locks, leader election, and concurrent access with real database.
 */
@SpringBootTest(classes = LeaderElectorImplIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("LeaderElectorImpl Integration Tests")
class LeaderElectorImplIntegrationTest extends AbstractEventProcessorTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private static final long TEST_LOCK_KEY = 1234567890L;

    @BeforeEach
    void setUp() {
        // No cleanup needed - advisory locks are automatically released on connection close
    }

    @Test
    @DisplayName("Should successfully acquire lock when available")
    void shouldSuccessfullyAcquireLock_WhenAvailable() {
        // Given
        LeaderElectorImpl elector = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, eventPublisher);

        // When
        boolean acquired = elector.tryAcquireGlobalLeader();

        // Then
        assertThat(acquired).isTrue();
        assertThat(elector.isGlobalLeader()).isTrue();
        assertThat(elector.getInstanceId()).isEqualTo("instance-1");
    }

    @Test
    @DisplayName("Should return false when lock already held by another instance")
    void shouldReturnFalse_WhenLockAlreadyHeldByAnotherInstance() throws Exception {
        // Given - First instance acquires lock and holds connection open
        // Note: PostgreSQL advisory locks are session-scoped, so we need to hold a connection
        LeaderElectorImpl elector1 = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, eventPublisher);
        
        // Hold lock by keeping connection open
        try (java.sql.Connection conn1 = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn1.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            stmt.setLong(1, TEST_LOCK_KEY);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue(); // Lock acquired
            }

            // When - Second instance tries to acquire same lock (while first holds it)
            LeaderElectorImpl elector2 = new LeaderElectorImpl(dataSource, "instance-2", TEST_LOCK_KEY, eventPublisher);
            boolean acquired = elector2.tryAcquireGlobalLeader();

            // Then
            assertThat(acquired).isFalse();
            assertThat(elector2.isGlobalLeader()).isFalse();
        }
        // Lock released when conn1 closes
    }

    @Test
    @DisplayName("Should release lock successfully")
    void shouldReleaseLock_Successfully() {
        // Given
        LeaderElectorImpl elector = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, eventPublisher);
        elector.tryAcquireGlobalLeader();
        assertThat(elector.isGlobalLeader()).isTrue();

        // When
        elector.releaseGlobalLeader();

        // Then
        assertThat(elector.isGlobalLeader()).isFalse();
    }

    @Test
    @DisplayName("Should allow lock acquisition after release")
    void shouldAllowLockAcquisition_AfterRelease() throws Exception {
        // Given - First instance acquires and holds lock
        LeaderElectorImpl elector1 = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, eventPublisher);
        LeaderElectorImpl elector2 = new LeaderElectorImpl(dataSource, "instance-2", TEST_LOCK_KEY, eventPublisher);
        
        // Hold lock by keeping connection open
        try (java.sql.Connection conn1 = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn1.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            stmt.setLong(1, TEST_LOCK_KEY);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }
            
            // Second instance should fail while first holds lock
            assertThat(elector2.tryAcquireGlobalLeader()).isFalse();
        }
        // Lock released when conn1 closes

        // When - Now second instance can acquire
        boolean acquired = elector2.tryAcquireGlobalLeader();

        // Then
        assertThat(acquired).isTrue();
        assertThat(elector2.isGlobalLeader()).isTrue();
    }

    @Test
    @DisplayName("Should handle concurrent lock attempts")
    void shouldHandleConcurrentLockAttempts() throws Exception {
        // Given - Hold lock with a persistent connection
        try (java.sql.Connection lockHolder = dataSource.getConnection();
             java.sql.PreparedStatement stmt = lockHolder.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            stmt.setLong(1, TEST_LOCK_KEY);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue(); // Lock held
            }

            int numThreads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<Boolean>> results = new ArrayList<>();
            CountDownLatch startLatch = new CountDownLatch(1);

            // When - Multiple threads try to acquire lock simultaneously (while lock is held)
            for (int i = 0; i < numThreads; i++) {
                final int instanceNum = i;
                Future<Boolean> future = executor.submit(() -> {
                    startLatch.await();
                    LeaderElectorImpl elector = new LeaderElectorImpl(
                        dataSource, "instance-" + instanceNum, TEST_LOCK_KEY, eventPublisher);
                    return elector.tryAcquireGlobalLeader();
                });
                results.add(future);
            }

            startLatch.countDown(); // Start all threads simultaneously

            // Then - None should succeed (lock is held)
            int successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(0); // None should acquire lock while it's held
            executor.shutdown();
        }
        // Lock released when lockHolder closes
    }

    @Test
    @DisplayName("Should isolate locks by different keys")
    void shouldIsolateLocks_ByDifferentKeys() {
        // Given
        long lockKey1 = 1111111111L;
        long lockKey2 = 2222222222L;

        LeaderElectorImpl elector1 = new LeaderElectorImpl(dataSource, "instance-1", lockKey1, eventPublisher);
        LeaderElectorImpl elector2 = new LeaderElectorImpl(dataSource, "instance-2", lockKey2, eventPublisher);

        // When - Both try to acquire locks with different keys
        boolean acquired1 = elector1.tryAcquireGlobalLeader();
        boolean acquired2 = elector2.tryAcquireGlobalLeader();

        // Then - Both should succeed (different keys don't conflict)
        assertThat(acquired1).isTrue();
        assertThat(acquired2).isTrue();
        assertThat(elector1.isGlobalLeader()).isTrue();
        assertThat(elector2.isGlobalLeader()).isTrue();
    }

    @Test
    @DisplayName("Should not release lock if not leader")
    void shouldNotReleaseLock_IfNotLeader() {
        // Given
        LeaderElectorImpl elector = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, eventPublisher);
        // Don't acquire lock - not leader

        // When
        elector.releaseGlobalLeader(); // Should be no-op

        // Then - Should not crash, and still not leader
        assertThat(elector.isGlobalLeader()).isFalse();
    }

    @Test
    @DisplayName("Should handle release when already released")
    void shouldHandleRelease_WhenAlreadyReleased() {
        // Given
        LeaderElectorImpl elector = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, eventPublisher);
        elector.tryAcquireGlobalLeader();
        elector.releaseGlobalLeader();

        // When - Release again
        elector.releaseGlobalLeader(); // Should be no-op

        // Then - Should not crash
        assertThat(elector.isGlobalLeader()).isFalse();
    }

    @Test
    @DisplayName("Should publish LeadershipMetric event on acquisition")
    void shouldPublishLeadershipMetricEvent_OnAcquisition() {
        // Given
        TestEventPublisher testPublisher = new TestEventPublisher();
        LeaderElectorImpl elector = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, testPublisher);

        // When
        elector.tryAcquireGlobalLeader();

        // Then
        assertThat(testPublisher.events).hasSize(1);
        assertThat(testPublisher.events.get(0)).isInstanceOf(LeadershipMetric.class);
        LeadershipMetric metric = (LeadershipMetric) testPublisher.events.get(0);
        assertThat(metric.instanceId()).isEqualTo("instance-1");
        assertThat(metric.isLeader()).isTrue();
    }

    @Test
    @DisplayName("Should publish LeadershipMetric event on release")
    void shouldPublishLeadershipMetricEvent_OnRelease() {
        // Given
        TestEventPublisher testPublisher = new TestEventPublisher();
        LeaderElectorImpl elector = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, testPublisher);
        elector.tryAcquireGlobalLeader();
        testPublisher.events.clear(); // Clear acquisition event

        // When
        elector.releaseGlobalLeader();

        // Then
        assertThat(testPublisher.events).hasSize(1);
        assertThat(testPublisher.events.get(0)).isInstanceOf(LeadershipMetric.class);
        LeadershipMetric metric = (LeadershipMetric) testPublisher.events.get(0);
        assertThat(metric.instanceId()).isEqualTo("instance-1");
        assertThat(metric.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Should publish LeadershipMetric event on failed acquisition")
    void shouldPublishLeadershipMetricEvent_OnFailedAcquisition() throws Exception {
        // Given - Hold lock with first connection
        TestEventPublisher testPublisher = new TestEventPublisher();
        LeaderElectorImpl elector2 = new LeaderElectorImpl(dataSource, "instance-2", TEST_LOCK_KEY, testPublisher);

        // Hold lock by keeping connection open
        try (java.sql.Connection conn1 = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn1.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            stmt.setLong(1, TEST_LOCK_KEY);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }

            // When - Second instance tries to acquire (should fail)
            elector2.tryAcquireGlobalLeader();

            // Then
            assertThat(testPublisher.events).hasSize(1);
            assertThat(testPublisher.events.get(0)).isInstanceOf(LeadershipMetric.class);
            LeadershipMetric metric = (LeadershipMetric) testPublisher.events.get(0);
            assertThat(metric.instanceId()).isEqualTo("instance-2");
            assertThat(metric.isLeader()).isFalse();
        }
    }

    @Test
    @DisplayName("Should validate constructor parameters")
    void shouldValidateConstructorParameters() {
        // Then - Null dataSource
        assertThatThrownBy(() -> new LeaderElectorImpl(null, "instance-1", TEST_LOCK_KEY, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSource must not be null");

        // Then - Null instanceId
        assertThatThrownBy(() -> new LeaderElectorImpl(dataSource, null, TEST_LOCK_KEY, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instanceId must not be null");

        // Then - Empty instanceId
        assertThatThrownBy(() -> new LeaderElectorImpl(dataSource, "", TEST_LOCK_KEY, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instanceId must not be null or empty");

        // Then - Null eventPublisher
        assertThatThrownBy(() -> new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventPublisher must not be null");
    }

    @Test
    @DisplayName("Should handle multiple acquire attempts from same instance")
    void shouldHandleMultipleAcquireAttempts_FromSameInstance() {
        // Given
        LeaderElectorImpl elector = new LeaderElectorImpl(dataSource, "instance-1", TEST_LOCK_KEY, eventPublisher);

        // When - Try to acquire multiple times
        boolean first = elector.tryAcquireGlobalLeader();
        boolean second = elector.tryAcquireGlobalLeader();

        // Then - First should succeed, second should also succeed (idempotent)
        // PostgreSQL advisory locks are idempotent - same connection can acquire same lock multiple times
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(elector.isGlobalLeader()).isTrue();
    }

    @Configuration
    static class TestConfig {
        @Bean
        public javax.sql.DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(postgres.getJdbcUrl());
            dataSource.setUsername(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            return dataSource;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public ApplicationEventPublisher eventPublisher() {
            return new TestEventPublisher();
        }
    }

    static class TestEventPublisher implements ApplicationEventPublisher {
        final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }
}

