package crablet.unit.outbox.impl;

import crablet.integration.AbstractCrabletIT;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.impl.OutboxLeaderElector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OutboxLeaderElector.
 * Tests leader election, lock acquisition, and heartbeat management.
 */
@DisplayName("OutboxLeaderElector Unit Tests")
class OutboxLeaderElectorTest extends AbstractCrabletIT {
    
    @Autowired
    private OutboxLeaderElector elector;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @BeforeEach
    void cleanUpLocks() {
        // Release all advisory locks before each test
        jdbcTemplate.execute("SELECT pg_advisory_unlock_all()");
        
        // Clean up database state
        jdbcTemplate.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
    }
    
    @Test
    @DisplayName("Should acquire global leader lock")
    void shouldAcquireGlobalLeader() {
        // When - Try to acquire global leader
        boolean acquired = elector.tryAcquireGlobalLeader();
        
        // Then - Should successfully acquire lock
        assertThat(acquired).isTrue();
        assertThat(elector.isGlobalLeader()).isTrue();
    }
    
    @Test
    @DisplayName("Should not acquire global leader if already held")
    void shouldNotAcquireGlobalLeaderIfAlreadyHeld() {
        // Given - First instance acquires lock
        elector.tryAcquireGlobalLeader();
        
        // When - Same instance tries again
        boolean acquiredAgain = elector.tryAcquireGlobalLeader();
        
        // Then - Should report as already held (still leader)
        assertThat(acquiredAgain).isTrue(); // Still the leader
        assertThat(elector.isGlobalLeader()).isTrue();
    }
    
    @Test
    @DisplayName("Should release global leader lock")
    void shouldReleaseGlobalLeader() {
        // Given - Acquire global leader
        elector.tryAcquireGlobalLeader();
        assertThat(elector.isGlobalLeader()).isTrue();
        
        // When - Release global leader
        elector.releaseGlobalLeader();
        
        // Then - Should no longer be leader
        assertThat(elector.isGlobalLeader()).isFalse();
    }
    
    @Test
    @DisplayName("Should acquire pair lock")
    void shouldAcquirePairLock() {
        // Given - A topic-publisher pair
        TopicPublisherPair pair = new TopicPublisherPair("test-topic", "CountDownLatchPublisher");
        
        // First, create the pair in database
        jdbcTemplate.update(
            "INSERT INTO outbox_topic_progress (topic, publisher, last_position, status) VALUES (?, ?, 0, 'ACTIVE')",
            pair.topic(), pair.publisher()
        );
        
        // When - Try to acquire the pair
        boolean acquired = elector.tryAcquirePair(pair);
        
        // Then - Should successfully acquire
        assertThat(acquired).isTrue();
        assertThat(elector.getOwnedPairs()).contains(pair);
    }
    
    @Test
    @DisplayName("Should detect stale heartbeat")
    void shouldDetectStaleHeartbeat() {
        // Given - A pair with stale heartbeat
        String topic = "test-topic";
        String publisher = "CountDownLatchPublisher";
        
        jdbcTemplate.update(
            """
            INSERT INTO outbox_topic_progress (topic, publisher, last_position, status, leader_instance, leader_heartbeat)
            VALUES (?, ?, 0, 'ACTIVE', 'old-instance', CURRENT_TIMESTAMP - INTERVAL '2 hours')
            """,
            topic, publisher
        );
        
        // When - Check if heartbeat is stale
        boolean isStale = elector.isHeartbeatStale(topic, publisher);
        
        // Then - Should detect as stale
        assertThat(isStale).isTrue();
    }
    
    @Test
    @DisplayName("Should detect fresh heartbeat")
    void shouldDetectFreshHeartbeat() {
        // Given - A pair with fresh heartbeat
        String topic = "test-topic";
        String publisher = "CountDownLatchPublisher";
        
        jdbcTemplate.update(
            """
            INSERT INTO outbox_topic_progress (topic, publisher, last_position, status, leader_instance, leader_heartbeat)
            VALUES (?, ?, 0, 'ACTIVE', 'current-instance', CURRENT_TIMESTAMP)
            """,
            topic, publisher
        );
        
        // When - Check if heartbeat is stale
        boolean isStale = elector.isHeartbeatStale(topic, publisher);
        
        // Then - Should detect as fresh (not stale)
        assertThat(isStale).isFalse();
    }
    
    @Test
    @DisplayName("Should update heartbeats for owned pairs")
    void shouldUpdateHeartbeats() throws InterruptedException {
        // Given - An owned pair
        TopicPublisherPair pair = new TopicPublisherPair("test-topic", "CountDownLatchPublisher");
        
        jdbcTemplate.update(
            "INSERT INTO outbox_topic_progress (topic, publisher, last_position, status) VALUES (?, ?, 0, 'ACTIVE')",
            pair.topic(), pair.publisher()
        );
        
        elector.tryAcquirePair(pair);
        
        // Record initial heartbeat time
        java.sql.Timestamp initialHeartbeat = jdbcTemplate.queryForObject(
            "SELECT leader_heartbeat FROM outbox_topic_progress WHERE topic = ? AND publisher = ?",
            java.sql.Timestamp.class,
            pair.topic(), pair.publisher()
        );
        
        // Wait a bit to ensure timestamp difference
        Thread.sleep(100);
        
        // When - Update heartbeats
        elector.updateHeartbeats();
        
        // Then - Heartbeat should be updated
        java.sql.Timestamp updatedHeartbeat = jdbcTemplate.queryForObject(
            "SELECT leader_heartbeat FROM outbox_topic_progress WHERE topic = ? AND publisher = ?",
            java.sql.Timestamp.class,
            pair.topic(), pair.publisher()
        );
        
        assertThat(updatedHeartbeat).isAfter(initialHeartbeat);
    }
    
    @Test
    @DisplayName("Should release all owned pairs")
    void shouldReleaseAllPairs() {
        // Given - Multiple owned pairs
        TopicPublisherPair pair1 = new TopicPublisherPair("topic1", "Publisher1");
        TopicPublisherPair pair2 = new TopicPublisherPair("topic2", "Publisher2");
        
        jdbcTemplate.update(
            "INSERT INTO outbox_topic_progress (topic, publisher, last_position, status) VALUES (?, ?, 0, 'ACTIVE')",
            pair1.topic(), pair1.publisher()
        );
        jdbcTemplate.update(
            "INSERT INTO outbox_topic_progress (topic, publisher, last_position, status) VALUES (?, ?, 0, 'ACTIVE')",
            pair2.topic(), pair2.publisher()
        );
        
        elector.tryAcquirePair(pair1);
        elector.tryAcquirePair(pair2);
        
        assertThat(elector.getOwnedPairs()).hasSize(2);
        
        // When - Release all pairs
        elector.releaseAllPairs();
        
        // Then - Should have no owned pairs
        assertThat(elector.getOwnedPairs()).isEmpty();
        
        // And - Database should reflect release
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE leader_instance IS NOT NULL",
            Integer.class
        );
        assertThat(count).isEqualTo(0);
    }
    
    @Test
    @DisplayName("Should return empty set when no pairs owned")
    void shouldReturnEmptySetWhenNoPairsOwned() {
        // When - Get owned pairs without acquiring any
        Set<TopicPublisherPair> ownedPairs = elector.getOwnedPairs();
        
        // Then - Should return empty set
        assertThat(ownedPairs).isEmpty();
    }
}

