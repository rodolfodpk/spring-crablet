package com.crablet.outbox.leader;

import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.InstanceIdProvider;
import com.crablet.outbox.metrics.LeadershipMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
     * public OutboxLeaderElector outboxLeaderElector(JdbcTemplate jdbcTemplate, OutboxConfig config, InstanceIdProvider instanceIdProvider, ApplicationEventPublisher eventPublisher) {
     *     return new OutboxLeaderElector(jdbcTemplate, config, instanceIdProvider, eventPublisher);
     * }
     * }</pre>
     */
public class OutboxLeaderElector {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxLeaderElector.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final String instanceId;
    
    // Advisory lock key for GLOBAL mode (hash of "crablet-outbox-processor")
    private static final long OUTBOX_LOCK_KEY = 4856221667890123456L;
    
    // Track leadership state
    private volatile boolean isGlobalLeader = false;
    
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Creates a new OutboxLeaderElector.
     *
     * @param jdbcTemplate JDBC template for database operations
     * @param config outbox configuration
     * @param instanceIdProvider provider for instance ID
     * @param eventPublisher event publisher for metrics (required).
     *                       Spring Boot automatically provides an ApplicationEventPublisher bean.
     *                       See crablet-metrics-micrometer for automatic metrics collection.
     */
    public OutboxLeaderElector(
            JdbcTemplate jdbcTemplate,
            OutboxConfig config,
            InstanceIdProvider instanceIdProvider,
            ApplicationEventPublisher eventPublisher) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (instanceIdProvider == null) {
            throw new IllegalArgumentException("instanceIdProvider must not be null");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.instanceId = instanceIdProvider.getInstanceId();
        this.eventPublisher = eventPublisher;
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
            eventPublisher.publishEvent(new LeadershipMetric(instanceId, true));
            log.info("✓ Acquired GLOBAL lock - this instance is the leader");
            return true;
        } else {
            isGlobalLeader = false;
            eventPublisher.publishEvent(new LeadershipMetric(instanceId, false));
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
            eventPublisher.publishEvent(new LeadershipMetric(instanceId, false));
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

