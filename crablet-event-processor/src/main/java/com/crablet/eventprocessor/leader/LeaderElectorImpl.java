package com.crablet.eventprocessor.leader;

import com.crablet.eventprocessor.metrics.LeadershipMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Generic implementation of LeaderElector using PostgreSQL advisory locks.
 * 
 * @param lockKey Advisory lock key (unique per processor type)
 */
public class LeaderElectorImpl implements LeaderElector {
    
    private static final Logger log = LoggerFactory.getLogger(LeaderElectorImpl.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final String instanceId;
    private final long lockKey;
    private final ApplicationEventPublisher eventPublisher;
    
    private volatile boolean isGlobalLeader = false;
    
    public LeaderElectorImpl(
            JdbcTemplate jdbcTemplate,
            String instanceId,
            long lockKey,
            ApplicationEventPublisher eventPublisher) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("instanceId must not be null or empty");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.instanceId = instanceId;
        this.lockKey = lockKey;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public boolean tryAcquireGlobalLeader() {
        Boolean lockAcquired = jdbcTemplate.queryForObject(
            "SELECT pg_try_advisory_lock(?)",
            Boolean.class,
            lockKey
        );
        
        if (Boolean.TRUE.equals(lockAcquired)) {
            isGlobalLeader = true;
            eventPublisher.publishEvent(new LeadershipMetric(instanceId, true));
            log.info("✓ Acquired lock (key: {}) - this instance is the leader", lockKey);
            return true;
        } else {
            isGlobalLeader = false;
            eventPublisher.publishEvent(new LeadershipMetric(instanceId, false));
            log.debug("✗ Another instance holds lock (key: {}) - this instance is follower", lockKey);
            return false;
        }
    }
    
    @Override
    public void releaseGlobalLeader() {
        if (isGlobalLeader) {
            jdbcTemplate.execute("SELECT pg_advisory_unlock(" + lockKey + ")");
            isGlobalLeader = false;
            eventPublisher.publishEvent(new LeadershipMetric(instanceId, false));
            log.info("Released lock (key: {})", lockKey);
        }
    }
    
    @Override
    public boolean isGlobalLeader() {
        return isGlobalLeader;
    }
    
    @Override
    public String getInstanceId() {
        return instanceId;
    }
}

