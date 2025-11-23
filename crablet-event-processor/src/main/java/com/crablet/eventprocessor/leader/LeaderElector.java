package com.crablet.eventprocessor.leader;

/**
 * Generic leader election using PostgreSQL advisory locks.
 */
public interface LeaderElector {
    
    /**
     * Try to acquire global leader lock.
     * @return true if lock acquired, false otherwise
     */
    boolean tryAcquireGlobalLeader();
    
    /**
     * Release global leader lock.
     */
    void releaseGlobalLeader();
    
    /**
     * Check if this instance is the global leader.
     */
    boolean isGlobalLeader();
    
    /**
     * Get instance ID.
     */
    String getInstanceId();
}

