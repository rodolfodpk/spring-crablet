package com.crablet.outbox.leader;

import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.metrics.OutboxMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;


    /**
     * Manages leader election using PostgreSQL advisory locks.
     * <p>
     * Uses GLOBAL lock strategy: one instance is the leader and processes all publishers.
     * <p>
     * <strong>Spring Integration:</strong>
     * Users must define as @Bean:
     * <pre>{@code
     * @Bean
     * public OutboxLeaderElector outboxLeaderElector(JdbcTemplate jdbcTemplate, OutboxConfig config, OutboxMetrics metrics) {
     *     return new OutboxLeaderElector(jdbcTemplate, config, metrics);
     * }
     * }</pre>
     */
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
     * Returns the instance ID for this leader elector.
     * Used for auto-registration of publishers.
     */
    public String getInstanceId() {
        return instanceId;
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

