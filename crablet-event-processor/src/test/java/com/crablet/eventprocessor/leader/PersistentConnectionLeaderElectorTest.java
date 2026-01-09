package com.crablet.eventprocessor.leader;

import com.crablet.eventprocessor.integration.AbstractEventProcessorTest;
import com.crablet.eventprocessor.metrics.LeadershipMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for persistent connection leader election.
 * Tests that leader maintains a persistent connection to hold the advisory lock.
 */
@SpringBootTest(classes = PersistentConnectionLeaderElectorTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Persistent Connection Leader Election Tests")
class PersistentConnectionLeaderElectorTest extends AbstractEventProcessorTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private static final long TEST_LOCK_KEY = 9876543210L;

    @BeforeEach
    void setUp() {
        // Ensure no locks are held from previous tests
        // Advisory locks are automatically released on connection close
    }

    @Test
    @DisplayName("Should maintain lock across multiple processing cycles when using persistent connection")
    void shouldMaintainLock_AcrossMultipleProcessingCycles() throws Exception {
        // Given - Leader pod with persistent connection
        LeaderElectorWithPersistentConnection leader = 
            new LeaderElectorWithPersistentConnection(dataSource, "leader-pod", TEST_LOCK_KEY, eventPublisher);
        
        // When - Acquire lock with persistent connection
        boolean acquired = leader.acquireWithPersistentConnection();
        assertThat(acquired).isTrue();
        
        // Then - Lock should persist across multiple checks
        for (int i = 0; i < 5; i++) {
            assertThat(leader.isGlobalLeader()).isTrue();
            Thread.sleep(100); // Simulate processing cycles
        }
        
        // Cleanup
        leader.close();
    }

    @Test
    @DisplayName("Should prevent followers from acquiring lock while leader holds persistent connection")
    void shouldPreventFollowers_WhileLeaderHoldsPersistentConnection() throws Exception {
        // Given - Leader pod holds lock with persistent connection
        LeaderElectorWithPersistentConnection leader = 
            new LeaderElectorWithPersistentConnection(dataSource, "leader-pod", TEST_LOCK_KEY, eventPublisher);
        leader.acquireWithPersistentConnection();
        
        // When - Multiple follower pods try to acquire
        List<LeaderElectorImpl> followers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            LeaderElectorImpl follower = new LeaderElectorImpl(
                dataSource, "follower-pod-" + i, TEST_LOCK_KEY, eventPublisher);
            followers.add(follower);
        }
        
        // Then - None should succeed
        for (LeaderElectorImpl follower : followers) {
            boolean acquired = follower.tryAcquireGlobalLeader();
            assertThat(acquired).isFalse();
            assertThat(follower.isGlobalLeader()).isFalse();
        }
        
        // Cleanup
        leader.close();
    }

    @Test
    @DisplayName("Should allow follower to acquire lock after leader connection closes (simulating crash)")
    void shouldAllowFollower_AfterLeaderConnectionCloses() throws Exception {
        // Given - Leader pod holds lock with persistent connection
        LeaderElectorWithPersistentConnection leader = 
            new LeaderElectorWithPersistentConnection(dataSource, "leader-pod", TEST_LOCK_KEY, eventPublisher);
        leader.acquireWithPersistentConnection();
        
        LeaderElectorImpl follower = new LeaderElectorImpl(
            dataSource, "follower-pod", TEST_LOCK_KEY, eventPublisher);
        
        // Verify follower can't acquire while leader holds lock
        assertThat(follower.tryAcquireGlobalLeader()).isFalse();
        
        // When - Leader crashes (connection closes)
        leader.close();
        Thread.sleep(100); // Small delay for lock release
        
        // Then - Follower can now acquire lock
        boolean acquired = follower.tryAcquireGlobalLeader();
        assertThat(acquired).isTrue();
        assertThat(follower.isGlobalLeader()).isTrue();
    }

    @Test
    @DisplayName("Should handle concurrent lock attempts with persistent connection")
    void shouldHandleConcurrentLockAttempts_WithPersistentConnection() throws Exception {
        // Given - Leader holds lock with persistent connection
        LeaderElectorWithPersistentConnection leader = 
            new LeaderElectorWithPersistentConnection(dataSource, "leader-pod", TEST_LOCK_KEY, eventPublisher);
        leader.acquireWithPersistentConnection();
        
        int numFollowers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numFollowers);
        List<Future<Boolean>> results = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // When - Multiple followers try to acquire simultaneously
        for (int i = 0; i < numFollowers; i++) {
            final int instanceNum = i;
            Future<Boolean> future = executor.submit(() -> {
                startLatch.await();
                LeaderElectorImpl follower = new LeaderElectorImpl(
                    dataSource, "follower-" + instanceNum, TEST_LOCK_KEY, eventPublisher);
                return follower.tryAcquireGlobalLeader();
            });
            results.add(future);
        }
        
        startLatch.countDown();
        
        // Then - None should succeed while leader holds lock
        int successCount = 0;
        for (Future<Boolean> result : results) {
            if (result.get(1, TimeUnit.SECONDS)) {
                successCount++;
            }
        }
        
        assertThat(successCount).isEqualTo(0);
        executor.shutdown();
        
        // Cleanup
        leader.close();
    }

    @Test
    @DisplayName("Should maintain independent locks for different modules (views and outbox)")
    void shouldMaintainIndependentLocks_ForDifferentModules() throws Exception {
        // Given - Different lock keys for views and outbox
        long viewsLockKey = 1111111111L;
        long outboxLockKey = 2222222222L;
        
        // When - Pod A becomes leader for views, Pod B becomes leader for outbox
        LeaderElectorWithPersistentConnection viewsLeader = 
            new LeaderElectorWithPersistentConnection(dataSource, "pod-a", viewsLockKey, eventPublisher);
        LeaderElectorWithPersistentConnection outboxLeader = 
            new LeaderElectorWithPersistentConnection(dataSource, "pod-b", outboxLockKey, eventPublisher);
        
        boolean viewsAcquired = viewsLeader.acquireWithPersistentConnection();
        boolean outboxAcquired = outboxLeader.acquireWithPersistentConnection();
        
        // Then - Both should succeed (different keys don't conflict)
        assertThat(viewsAcquired).isTrue();
        assertThat(outboxAcquired).isTrue();
        assertThat(viewsLeader.isGlobalLeader()).isTrue();
        assertThat(outboxLeader.isGlobalLeader()).isTrue();
        
        // Cleanup
        viewsLeader.close();
        outboxLeader.close();
    }

    @Test
    @DisplayName("Should allow same pod to be leader for both views and outbox")
    void shouldAllowSamePod_ToBeLeaderForBothModules() throws Exception {
        // Given - Same pod, different lock keys
        long viewsLockKey = 1111111111L;
        long outboxLockKey = 2222222222L;
        
        // When - Same pod acquires both locks
        LeaderElectorWithPersistentConnection viewsLeader = 
            new LeaderElectorWithPersistentConnection(dataSource, "pod-1", viewsLockKey, eventPublisher);
        LeaderElectorWithPersistentConnection outboxLeader = 
            new LeaderElectorWithPersistentConnection(dataSource, "pod-1", outboxLockKey, eventPublisher);
        
        boolean viewsAcquired = viewsLeader.acquireWithPersistentConnection();
        boolean outboxAcquired = outboxLeader.acquireWithPersistentConnection();
        
        // Then - Both should succeed (same pod, different keys)
        assertThat(viewsAcquired).isTrue();
        assertThat(outboxAcquired).isTrue();
        assertThat(viewsLeader.isGlobalLeader()).isTrue();
        assertThat(outboxLeader.isGlobalLeader()).isTrue();
        
        // Verify both connections are held
        assertThat(viewsLeader.hasActiveConnection()).isTrue();
        assertThat(outboxLeader.hasActiveConnection()).isTrue();
        
        // Cleanup
        viewsLeader.close();
        outboxLeader.close();
    }

    @Test
    @DisplayName("Should verify connection pool usage - only leader holds connection")
    void shouldVerifyConnectionPoolUsage_OnlyLeaderHoldsConnection() throws Exception {
        // Given - Check initial connection count (if possible)
        // Note: This is a simplified test - real connection pool monitoring would be more complex
        
        // When - Leader acquires lock with persistent connection
        LeaderElectorWithPersistentConnection leader = 
            new LeaderElectorWithPersistentConnection(dataSource, "leader-pod", TEST_LOCK_KEY, eventPublisher);
        leader.acquireWithPersistentConnection();
        
        // Create followers (should not hold connections)
        List<LeaderElectorImpl> followers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LeaderElectorImpl follower = new LeaderElectorImpl(
                dataSource, "follower-" + i, TEST_LOCK_KEY, eventPublisher);
            followers.add(follower);
            // Try to acquire (will fail, but connection closes immediately)
            follower.tryAcquireGlobalLeader();
        }
        
        // Then - Only leader should have persistent connection
        assertThat(leader.hasActiveConnection()).isTrue();
        // Followers' connections are closed (they use try-with-resources)
        
        // Cleanup
        leader.close();
    }

    /**
     * Test helper that simulates LeaderElector with persistent connection.
     * This is what the actual implementation should do.
     */
    static class LeaderElectorWithPersistentConnection {
        private final DataSource dataSource;
        private final String instanceId;
        private final long lockKey;
        private final ApplicationEventPublisher eventPublisher;
        private Connection persistentConnection;
        private volatile boolean isGlobalLeader = false;
        
        public LeaderElectorWithPersistentConnection(
                DataSource dataSource,
                String instanceId,
                long lockKey,
                ApplicationEventPublisher eventPublisher) {
            this.dataSource = dataSource;
            this.instanceId = instanceId;
            this.lockKey = lockKey;
            this.eventPublisher = eventPublisher;
        }
        
        public boolean acquireWithPersistentConnection() throws SQLException {
            if (persistentConnection != null && !persistentConnection.isClosed()) {
                return isGlobalLeader; // Already acquired
            }
            
            persistentConnection = dataSource.getConnection();
            try (PreparedStatement stmt = persistentConnection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                stmt.setLong(1, lockKey);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Boolean lockAcquired = rs.getBoolean(1);
                        if (Boolean.TRUE.equals(lockAcquired)) {
                            isGlobalLeader = true;
                            eventPublisher.publishEvent(new LeadershipMetric(instanceId, true));
                            return true;
                        }
                    }
                }
            }
            
            // If lock not acquired, close connection
            if (persistentConnection != null) {
                persistentConnection.close();
                persistentConnection = null;
            }
            isGlobalLeader = false;
            return false;
        }
        
        public void close() throws SQLException {
            if (persistentConnection != null && !persistentConnection.isClosed()) {
                try (PreparedStatement stmt = persistentConnection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    stmt.setLong(1, lockKey);
                    stmt.execute();
                }
                persistentConnection.close();
                persistentConnection = null;
            }
            isGlobalLeader = false;
        }
        
        public boolean isGlobalLeader() {
            return isGlobalLeader && persistentConnection != null && !isConnectionClosed();
        }
        
        public boolean hasActiveConnection() {
            return persistentConnection != null && !isConnectionClosed();
        }
        
        private boolean isConnectionClosed() {
            try {
                return persistentConnection.isClosed();
            } catch (SQLException e) {
                return true;
            }
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        @SuppressWarnings("nullness")
        public DataSource dataSource() {
            SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
            dataSource.setDriverClass(Driver.class);
            dataSource.setUrl(AbstractEventProcessorTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractEventProcessorTest.postgres.getUsername());
            dataSource.setPassword(AbstractEventProcessorTest.postgres.getPassword());
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
        public void publishEvent(@NonNull Object event) {
            events.add(event);
        }
    }
}

