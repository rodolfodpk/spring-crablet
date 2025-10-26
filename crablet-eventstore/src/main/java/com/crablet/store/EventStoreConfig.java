package com.crablet.store;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for EventStore behavior.
 * Controls command persistence and transaction isolation levels.
 * <p>
 * Located in crablet.impl since it's a Spring-specific implementation detail
 * used only by implementation classes (EventStoreImpl and CommandExecutorImpl).
 */
@Component
@ConfigurationProperties(prefix = "crablet.eventstore")
public class EventStoreConfig {
    // Note: Spring Boot will automatically bind properties from application.properties
    // to these fields via setters

    private boolean persistCommands = true;
    private String transactionIsolation = "READ_COMMITTED";
    private int fetchSize = 1000;

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

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }
}
