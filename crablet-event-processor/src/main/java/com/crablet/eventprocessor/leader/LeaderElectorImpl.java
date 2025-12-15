package com.crablet.eventprocessor.leader;

import com.crablet.eventprocessor.metrics.LeadershipMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Generic implementation of LeaderElector using PostgreSQL advisory locks.
 * 
 * <p>Uses plain JDBC for consistency with eventstore module and full control.
 * 
 * @param lockKey Advisory lock key (unique per processor type)
 */
public class LeaderElectorImpl implements LeaderElector {
    
    private static final Logger log = LoggerFactory.getLogger(LeaderElectorImpl.class);
    
    private final DataSource dataSource;
    private final String instanceId;
    private final long lockKey;
    private final ApplicationEventPublisher eventPublisher;
    
    private static final String TRY_ACQUIRE_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String RELEASE_LOCK_SQL = "SELECT pg_advisory_unlock(?)";
    
    private volatile boolean isGlobalLeader = false;
    
    public LeaderElectorImpl(
            DataSource dataSource,
            String instanceId,
            long lockKey,
            ApplicationEventPublisher eventPublisher) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("instanceId must not be null or empty");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.dataSource = dataSource;
        this.instanceId = instanceId;
        this.lockKey = lockKey;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public boolean tryAcquireGlobalLeader() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(TRY_ACQUIRE_LOCK_SQL)) {
            
            stmt.setLong(1, lockKey);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Boolean lockAcquired = rs.getBoolean(1);
                    
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
                
                // Should not happen, but handle gracefully
                isGlobalLeader = false;
                return false;
            }
        } catch (SQLException e) {
            log.error("Failed to acquire lock (key: {}): {}", lockKey, e.getMessage(), e);
            isGlobalLeader = false;
            return false;
        }
    }
    
    @Override
    public void releaseGlobalLeader() {
        if (isGlobalLeader) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(RELEASE_LOCK_SQL)) {
                
                stmt.setLong(1, lockKey);
                stmt.execute();
                
                isGlobalLeader = false;
                eventPublisher.publishEvent(new LeadershipMetric(instanceId, false));
                log.info("Released lock (key: {})", lockKey);
            } catch (SQLException e) {
                log.error("Failed to release lock (key: {}): {}", lockKey, e.getMessage(), e);
                // Still mark as not leader even if release failed
                isGlobalLeader = false;
            }
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

