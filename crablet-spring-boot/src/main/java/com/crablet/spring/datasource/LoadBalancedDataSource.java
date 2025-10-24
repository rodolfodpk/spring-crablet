package com.crablet.spring.datasource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load-balanced DataSource that distributes read operations across multiple replica databases.
 * <p>
 * This implementation uses a simple round-robin strategy to rotate between available replicas.
 * If all replicas fail and fallback is enabled, connections are routed to the primary database.
 * <p>
 * <strong>Use cases:</strong>
 * <ul>
 *   <li>Read scaling: Distribute read queries across read replicas</li>
 *   <li>Failover: Automatic fallback to primary if replicas unavailable</li>
 *   <li>Performance: Reduce load on primary database</li>
 * </ul>
 * <p>
 * <strong>Important:</strong> This DataSource should NOT be used for write operations or
 * operations requiring strict consistency (e.g., outbox position tracking).
 */
public class LoadBalancedDataSource extends AbstractDataSource {
    
    private final List<DataSource> replicaDataSources;
    private final DataSource primaryDataSource;
    private final boolean fallbackToPrimary;
    private final AtomicInteger counter = new AtomicInteger(0);
    
    /**
     * Creates a new LoadBalancedDataSource.
     * 
     * @param replicaDataSources List of DataSources for read replicas
     * @param primaryDataSource Primary DataSource for fallback
     * @param fallbackToPrimary Whether to fallback to primary if all replicas fail
     */
    public LoadBalancedDataSource(
            List<DataSource> replicaDataSources,
            DataSource primaryDataSource,
            boolean fallbackToPrimary) {
        if (replicaDataSources == null || replicaDataSources.isEmpty()) {
            throw new IllegalArgumentException("replicaDataSources must not be null or empty");
        }
        if (primaryDataSource == null) {
            throw new IllegalArgumentException("primaryDataSource must not be null");
        }
        this.replicaDataSources = replicaDataSources;
        this.primaryDataSource = primaryDataSource;
        this.fallbackToPrimary = fallbackToPrimary;
    }
    
    /**
     * Gets a connection from one of the replica DataSources using round-robin selection.
     * <p>
     * If all replicas fail and fallback is enabled, returns a connection from the primary database.
     * 
     * @return A connection from an available replica (or primary if fallback enabled)
     * @throws SQLException If no replicas are available and fallback is disabled
     */
    @Override
    public Connection getConnection() throws SQLException {
        int attempts = replicaDataSources.size();
        while (attempts-- > 0) {
            int index = counter.getAndIncrement() % replicaDataSources.size();
            try {
                return replicaDataSources.get(index).getConnection();
            } catch (SQLException e) {
                // Try next replica on failure
            }
        }
        
        // All replicas failed - fallback to primary if enabled
        if (fallbackToPrimary) {
            return primaryDataSource.getConnection();
        }
        
        throw new SQLException("All read replicas unavailable and fallback to primary is disabled");
    }
    
    /**
     * Gets a connection with the specified username and password.
     * <p>
     * Delegates to {@link #getConnection()} since credentials are already
     * configured in the individual DataSources.
     * 
     * @param username Username (ignored - credentials configured in DataSources)
     * @param password Password (ignored - credentials configured in DataSources)
     * @return A connection from an available replica
     * @throws SQLException If no replicas are available
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // Credentials are already configured in individual DataSources
        return getConnection();
    }
}

