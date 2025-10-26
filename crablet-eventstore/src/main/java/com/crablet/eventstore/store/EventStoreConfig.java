package com.crablet.eventstore.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for EventStore behavior.
 * Controls command persistence and transaction isolation levels.
 * <p>
 * <strong>Spring Integration:</strong>
 * Users must define as @Bean:
 * <pre>{@code
 * @Bean
 * @ConfigurationProperties(prefix = "crablet.eventstore")
 * public EventStoreConfig eventStoreConfig() {
 *     return new EventStoreConfig();
 * }
 * }</pre>
 */
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
