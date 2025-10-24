package com.crablet.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for read replica support.
 * <p>
 * These properties control whether read operations should be routed to read replicas
 * for horizontal scaling. When disabled (default), all operations use the primary database.
 */
@ConfigurationProperties(prefix = "crablet.eventstore.read-replicas")
public class ReadReplicaProperties {
    
    /**
     * Whether read replica support is enabled.
     * Default: false (all operations use primary database)
     */
    private boolean enabled = false;
    
    /**
     * Comma-separated list of read replica JDBC URLs.
     * Example: "jdbc:postgresql://replica1:5432/db,jdbc:postgresql://replica2:5432/db"
     */
    private List<String> urls = new ArrayList<>();
    
    /**
     * Whether to fallback to primary database if all replicas fail.
     * Default: true (ensures reads always succeed)
     */
    private boolean fallbackToPrimary = true;
    
    /**
     * HikariCP connection pool configuration for read replicas.
     */
    private HikariProperties hikari = new HikariProperties();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<String> getUrls() {
        return urls;
    }
    
    public void setUrls(List<String> urls) {
        this.urls = urls;
    }
    
    public boolean isFallbackToPrimary() {
        return fallbackToPrimary;
    }
    
    public void setFallbackToPrimary(boolean fallbackToPrimary) {
        this.fallbackToPrimary = fallbackToPrimary;
    }
    
    public HikariProperties getHikari() {
        return hikari;
    }
    
    public void setHikari(HikariProperties hikari) {
        this.hikari = hikari;
    }
    
    /**
     * HikariCP-specific configuration for read replica connection pools.
     */
    public static class HikariProperties {
        private String username;
        private String password;
        private int maximumPoolSize = 50;
        private int minimumIdle = 10;
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }
        
        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }
        
        public int getMinimumIdle() {
            return minimumIdle;
        }
        
        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }
    }
}

