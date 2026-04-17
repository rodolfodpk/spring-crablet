package com.crablet.eventpoller.internal;

import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.metrics.LeadershipMetric;
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
 */
public class LeaderElectorImpl implements LeaderElector {

    private static final Logger log = LoggerFactory.getLogger(LeaderElectorImpl.class);

    private final DataSource dataSource;
    private final String processorId;
    private final String instanceId;
    private final long lockKey;
    private final ApplicationEventPublisher eventPublisher;

    private static final String TRY_ACQUIRE_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String RELEASE_LOCK_SQL = "SELECT pg_advisory_unlock(?)";

    private Connection leaderConnection;
    private volatile boolean isGlobalLeader = false;

    /**
     * Creates a leader elector backed by a PostgreSQL advisory lock.
     *
     * @param dataSource the data source used to acquire and hold the lock
     * @param processorId the processor identifier used in logs and metrics
     * @param instanceId the current application instance identifier
     * @param lockKey advisory lock key, unique per processor type
     * @param eventPublisher event publisher for leadership metrics
     */
    public LeaderElectorImpl(
            DataSource dataSource,
            String processorId,
            String instanceId,
            long lockKey,
            ApplicationEventPublisher eventPublisher) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        if (processorId == null || processorId.isEmpty()) {
            throw new IllegalArgumentException("processorId must not be null or empty");
        }
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("instanceId must not be null or empty");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.dataSource = dataSource;
        this.processorId = processorId;
        this.instanceId = instanceId;
        this.lockKey = lockKey;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public synchronized boolean tryAcquireGlobalLeader() {
        if (hasActiveLeaderConnection()) {
            return true;
        }

        closeLeaderConnectionQuietly();

        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement stmt = connection.prepareStatement(TRY_ACQUIRE_LOCK_SQL)) {
                stmt.setLong(1, lockKey);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) {
                        leaderConnection = connection;
                        isGlobalLeader = true;
                        eventPublisher.publishEvent(new LeadershipMetric(processorId, instanceId, true));
                        log.info("Acquired lock (key: {}) - this instance is the leader", lockKey);
                        return true;
                    }
                }
            }

            connection.close();
            isGlobalLeader = false;
            eventPublisher.publishEvent(new LeadershipMetric(processorId, instanceId, false));
            log.debug("Another instance holds lock (key: {}) - this instance is follower", lockKey);
            return false;
        } catch (SQLException e) {
            closeLeaderConnectionQuietly();
            log.error("Failed to acquire lock (key: {}): {}", lockKey, e.getMessage(), e);
            isGlobalLeader = false;
            return false;
        }
    }

    @Override
    public synchronized void releaseGlobalLeader() {
        if (!hasActiveLeaderConnection()) {
            closeLeaderConnectionQuietly();
            isGlobalLeader = false;
            return;
        }

        try (PreparedStatement stmt = leaderConnection.prepareStatement(RELEASE_LOCK_SQL)) {
            stmt.setLong(1, lockKey);
            stmt.execute();

            log.info("Released lock (key: {})", lockKey);
        } catch (SQLException e) {
            log.error("Failed to release lock (key: {}): {}", lockKey, e.getMessage(), e);
        } finally {
            closeLeaderConnectionQuietly();
            isGlobalLeader = false;
            eventPublisher.publishEvent(new LeadershipMetric(processorId, instanceId, false));
        }
    }

    @Override
    public synchronized boolean isGlobalLeader() {
        if (!hasActiveLeaderConnection()) {
            closeLeaderConnectionQuietly();
            isGlobalLeader = false;
            return false;
        }
        return true;
    }

    private boolean hasActiveLeaderConnection() {
        if (!isGlobalLeader || leaderConnection == null) {
            return false;
        }
        try {
            return !leaderConnection.isClosed();
        } catch (SQLException e) {
            log.warn("Leader connection check failed for lock {}: {}", lockKey, e.getMessage());
            return false;
        }
    }

    private void closeLeaderConnectionQuietly() {
        if (leaderConnection == null) {
            return;
        }
        try {
            if (!leaderConnection.isClosed()) {
                leaderConnection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close leader connection for lock {}: {}", lockKey, e.getMessage());
        } finally {
            leaderConnection = null;
        }
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }
}
