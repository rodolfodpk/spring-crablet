package com.crablet.outbox.impl;

import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.impl.OutboxMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages leader election, advisory locks, and heartbeats for the outbox processor.
 * <p>
 * Supports two lock strategies:
 * - GLOBAL: Single lock for all publishers (one instance processes everything)
 * - PER_TOPIC_PUBLISHER: One lock per (topic, publisher) pair (distributed processing)
 * <p>
 * Uses PostgreSQL advisory locks for leader election and database heartbeats for failure detection.
 */
@Component
public class OutboxLeaderElector {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxLeaderElector.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final OutboxConfig config;
    private final OutboxMetrics outboxMetrics;
    private final String instanceId;
    
    // Advisory lock key for GLOBAL mode (hash of "crablet-outbox-processor")
    private static final long OUTBOX_LOCK_KEY = 4856221667890123456L;
    
    // SQL statements for leader election and heartbeat management
    private static final String RELEASE_PAIR_SQL = """
        UPDATE outbox_topic_progress
        SET leader_instance = NULL,
            leader_heartbeat = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE topic = ? AND publisher = ? AND leader_instance = ?
        """;

    private static final String UPDATE_PAIR_HEARTBEAT_SQL = """
        UPDATE outbox_topic_progress
        SET leader_heartbeat = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE topic = ? AND publisher = ? AND leader_instance = ?
        """;
    
    // Track leadership state
    private volatile boolean isGlobalLeader = false;
    private final Set<TopicPublisherPair> ownedPairs = ConcurrentHashMap.newKeySet();
    
    public OutboxLeaderElector(
            JdbcTemplate jdbcTemplate,
            OutboxConfig config,
            OutboxMetrics outboxMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
        this.outboxMetrics = outboxMetrics;
        this.instanceId = outboxMetrics.getInstanceId();
    }
    
    /**
     * Try to acquire global leader lock (GLOBAL strategy).
     * @return true if lock acquired, false otherwise
     */
    public boolean tryAcquireGlobalLeader() {
        log.info("Attempting to acquire GLOBAL lock...");
        Boolean lockAcquired = tryAcquireLock(OUTBOX_LOCK_KEY);
        if (Boolean.TRUE.equals(lockAcquired)) {
            isGlobalLeader = true;
            outboxMetrics.setLeader(true);
            log.info("✓ Acquired GLOBAL lock - this instance is the leader");
            return true;
        } else {
            isGlobalLeader = false;
            outboxMetrics.setLeader(false);
            log.info("✗ Another instance holds GLOBAL lock - this instance is follower");
            return false;
        }
    }
    
    /**
     * Release global leader lock (GLOBAL strategy).
     */
    public void releaseGlobalLeader() {
        if (isGlobalLeader) {
            releaseLock(OUTBOX_LOCK_KEY);
            isGlobalLeader = false;
            outboxMetrics.setLeader(false);
            log.info("Released GLOBAL lock");
        }
    }
    
    /**
     * Check if this instance is the global leader.
     */
    public boolean isGlobalLeader() {
        return isGlobalLeader;
    }
    
    /**
     * Initialize all (topic, publisher) pairs on startup (PER_TOPIC_PUBLISHER strategy).
     */
    public void initializeAllPairs() {
        log.info("Attempting to acquire (topic, publisher) pair locks (PER_TOPIC_PUBLISHER mode)...");
        Map<String, TopicConfig> topicConfigs = config.getTopics();
        
        for (var entry : topicConfigs.entrySet()) {
            String topicName = entry.getKey();
            TopicConfig topicConfig = entry.getValue();
            Set<String> configuredPublishers = topicConfig.getPublishers();
            
            for (String publisherName : configuredPublishers) {
                TopicPublisherPair pair = new TopicPublisherPair(topicName, publisherName);
                tryAcquirePair(pair);
            }
        }
        
        log.info("This instance owns {} pairs", ownedPairs.size());
    }
    
    /**
     * Try to acquire a (topic, publisher) pair using hybrid lock + heartbeat approach.
     * @return true if pair acquired, false otherwise
     */
    public boolean tryAcquirePair(TopicPublisherPair pair) {
        if (ownedPairs.contains(pair)) {
            return true;
        }
        
        long lockKey = pair.getLockKey();
        Boolean lockAcquired = tryAcquireLock(lockKey);
        if (Boolean.FALSE.equals(lockAcquired)) {
            Boolean isStale = isHeartbeatStale(pair.topic(), pair.publisher());
            if (Boolean.FALSE.equals(isStale)) {
                return false;
            }
            log.debug("Pair {} has stale heartbeat but lock is held - waiting for connection drop", pair);
            return false;
        }
        
        // Use make_interval() for better compatibility with prepared statements
        int updated = jdbcTemplate.update(
            """
            UPDATE outbox_topic_progress
            SET leader_instance = ?,
                leader_heartbeat = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE topic = ? AND publisher = ?
              AND (
                leader_instance IS NULL 
                OR leader_instance = ?
                OR leader_heartbeat < CURRENT_TIMESTAMP - make_interval(secs => ?)
              )
            """,
            instanceId, pair.topic(), pair.publisher(), instanceId, config.getHeartbeatTtlSeconds()
        );
        
        if (updated > 0) {
            ownedPairs.add(pair);
            outboxMetrics.setLeaderForPublisher(pair.topic(), true);
            log.info("✓ Acquired pair: {}", pair);
            return true;
        } else {
            releaseLock(lockKey);
            log.debug("✗ Lost race for pair: {}", pair);
            return false;
        }
    }
    
    /**
     * Try to acquire all available pairs (unowned or with stale heartbeats).
     * Called periodically for dynamic scaling and failover.
     * @return number of newly acquired pairs
     */
    public int tryAcquireAvailablePairs() {
        Map<String, TopicConfig> topicConfigs = config.getTopics();
        int acquired = 0;
        
        for (var entry : topicConfigs.entrySet()) {
            String topicName = entry.getKey();
            TopicConfig topicConfig = entry.getValue();
            Set<String> configuredPublishers = topicConfig.getPublishers();
            
            for (String publisherName : configuredPublishers) {
                TopicPublisherPair pair = new TopicPublisherPair(topicName, publisherName);
                if (tryAcquirePair(pair)) {
                    acquired++;
                }
            }
        }
        
        if (acquired > 0) {
            log.info("Acquired {} new pairs (failover or scale-up)", acquired);
        }
        
        return acquired;
    }
    
    /**
     * Release all owned pair locks (PER_TOPIC_PUBLISHER strategy).
     */
    public void releaseAllPairs() {
        log.info("Releasing (topic, publisher) pair ownership...");
        for (TopicPublisherPair pair : ownedPairs) {
            long lockKey = pair.getLockKey();
            releaseLock(lockKey);
            jdbcTemplate.update(
                RELEASE_PAIR_SQL,
                pair.topic(), pair.publisher(), instanceId
            );
            outboxMetrics.setLeaderForPublisher(pair.topic(), false);
            log.info("Released ownership of pair: {}", pair);
        }
        ownedPairs.clear();
        log.info("All pair locks released");
    }
    
    /**
     * Get the set of pairs currently owned by this instance.
     */
    public Set<TopicPublisherPair> getOwnedPairs() {
        return Set.copyOf(ownedPairs);
    }
    
    /**
     * Update heartbeats for all owned pairs.
     * Called periodically to prove this instance is still alive.
     */
    public void updateHeartbeats() {
        if (ownedPairs.isEmpty()) {
            return;
        }
        for (TopicPublisherPair pair : ownedPairs) {
            try {
                jdbcTemplate.update(
                    UPDATE_PAIR_HEARTBEAT_SQL,
                    pair.topic(), pair.publisher(), instanceId
                );
            } catch (Exception e) {
                log.warn("Failed to update heartbeat for pair {}: {}", pair, e.getMessage());
            }
        }
    }
    
    /**
     * Check if a pair's heartbeat is stale (leader is dead).
     */
    public boolean isHeartbeatStale(String topic, String publisher) {
        try {
            // Use make_interval() for better compatibility and COALESCE for NULL safety
            Boolean isStale = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    leader_heartbeat < CURRENT_TIMESTAMP - make_interval(secs => ?)
                    OR leader_heartbeat IS NULL,
                    true
                )
                FROM outbox_topic_progress
                WHERE topic = ? AND publisher = ?
                """,
                Boolean.class,
                config.getHeartbeatTtlSeconds(),
                topic,
                publisher
            );
            return Boolean.TRUE.equals(isStale);
        } catch (Exception e) {
            log.warn("Failed to check heartbeat staleness for {}/{}: {}", topic, publisher, e.getMessage());
            return false;
        }
    }
    
    /**
     * Try to acquire an advisory lock (non-blocking).
     */
    private Boolean tryAcquireLock(long lockKey) {
        return jdbcTemplate.queryForObject(
            "SELECT pg_try_advisory_lock(?)",
            Boolean.class,
            lockKey
        );
    }
    
    /**
     * Release an advisory lock.
     */
    private void releaseLock(long lockKey) {
        jdbcTemplate.execute("SELECT pg_advisory_unlock(" + lockKey + ")");
    }
}

