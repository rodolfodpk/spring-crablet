package com.crablet.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for EventStore behavior.
 * Controls command persistence and transaction isolation levels.
 * 
 * Located in crablet.core since CommandExecutor (main consumer) is here,
 * and crablet.core is already Spring-coupled.
 */
@Component
@ConfigurationProperties(prefix = "crablet.eventstore")
public class EventStoreConfig {
    // Note: Spring Boot will automatically bind properties from application.properties
    // to these fields via setters
    
    private boolean persistCommands = true;
    private String transactionIsolation = "READ_COMMITTED";
    
    public boolean isPersistCommands() { 
        return persistCommands; 
    }
    
    public void setPersistCommands(boolean persistCommands) { 
        this.persistCommands = persistCommands; 
    }
    
    public String getTransactionIsolation() { 
        return transactionIsolation; 
    }
    
    public void setTransactionIsolation(String transactionIsolation) { 
        this.transactionIsolation = transactionIsolation; 
    }
}

