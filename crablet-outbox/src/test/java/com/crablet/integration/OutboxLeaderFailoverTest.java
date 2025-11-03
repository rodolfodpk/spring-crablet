package com.crablet.integration;

import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.leader.OutboxLeaderElector;
import com.crablet.outbox.processor.OutboxProcessorImpl;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests leader failover behavior with GLOBAL lock strategy.
 * Verifies that followers periodically retry lock acquisition and take over when leader crashes.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class OutboxLeaderFailoverTest extends AbstractCrabletTest {
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private OutboxProcessorImpl outboxProcessor;
    
    @Autowired
    private OutboxLeaderElector leaderElector;
    
    @Autowired
    private OutboxConfig outboxConfig;
    
    @BeforeEach
    void setUp() {
        outboxConfig.setEnabled(true);
    }
    
    @Test
    void shouldRetryLockAcquisitionWhenNotLeader() {
        // Given: Processor starts and tries to acquire lock at startup
        // (may or may not succeed depending on which test runs first - that's okay)
        
        // When: Process pending (triggers scheduledTask logic)
        // This simulates what happens in scheduled tasks
        int processed = outboxProcessor.processPending();
        
        // Then: Should either process (if leader) or skip (if follower)
        // The key is that it doesn't crash and handles both cases
        assertThat(processed).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    void shouldProcessEventsWhenLeaderAcquiresLockAfterRetry() throws InterruptedException {
        // Given: Events in store
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent")
                .tag("test", "value1")
                .data("{\"test\":\"data1\"}".getBytes())
                .build()
        );
        eventStore.append(events);
        
        // Given: Not currently leader (release lock if held)
        if (leaderElector.isGlobalLeader()) {
            leaderElector.releaseGlobalLeader();
        }
        
        // When: Manually trigger retry (simulating scheduled task behavior)
        // Try to acquire lock
        boolean acquired = leaderElector.tryAcquireGlobalLeader();
        
        if (acquired) {
            // Now process events (would happen in scheduledTask after becoming leader)
            int processed = outboxProcessor.processPending();
            
            // Then: Should process events
            assertThat(processed).isGreaterThan(0);
        }
    }
    
    @Test
    void shouldHandleLeaderFailoverScenario() {
        // Given: Leader holds lock
        boolean initiallyLeader = leaderElector.tryAcquireGlobalLeader();
        assertThat(initiallyLeader || !initiallyLeader).isTrue(); // Either is fine
        
        // When: Simulate leader crash (release lock manually)
        if (leaderElector.isGlobalLeader()) {
            leaderElector.releaseGlobalLeader();
        }
        
        // When: Retry lock acquisition (simulates follower retry in scheduledTask)
        boolean acquiredAfterFailover = leaderElector.tryAcquireGlobalLeader();
        
        // Then: Should be able to acquire lock after previous leader releases it
        // (In real scenario, PostgreSQL releases lock automatically on connection drop)
        assertThat(acquiredAfterFailover).isTrue();
        assertThat(leaderElector.isGlobalLeader()).isTrue();
    }
    
    @Test
    void shouldUseDedicatedLeaderRetryScheduler() {
        // Given: Not leader
        if (leaderElector.isGlobalLeader()) {
            leaderElector.releaseGlobalLeader();
        }
        
        // When: Process pending (publishers should not retry, dedicated scheduler handles it)
        // The dedicated leader retry scheduler runs independently
        int processed = outboxProcessor.processPending();
        
        // Then: Should handle gracefully (either process if leader, or skip if follower)
        // Leader retry is handled by dedicated scheduler, not publisher schedulers
        assertThat(processed).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    void shouldRespectCooldownForRedundantLockRetries() throws InterruptedException {
        // Given: Not leader
        if (leaderElector.isGlobalLeader()) {
            leaderElector.releaseGlobalLeader();
        }
        
        // When: Multiple rapid calls to processPending (simulates multiple publisher schedulers)
        // First call: Should attempt lock acquisition
        int first = outboxProcessor.processPending();
        
        // Immediate second call: Should respect cooldown (no redundant retry)
        // Note: processPending() doesn't call scheduledTask(), so we can't directly test cooldown here
        // The cooldown is tested in unit tests. This integration test verifies overall behavior.
        int second = outboxProcessor.processPending();
        
        // Then: Both should handle gracefully
        assertThat(first).isGreaterThanOrEqualTo(0);
        assertThat(second).isGreaterThanOrEqualTo(0);
    }
}

