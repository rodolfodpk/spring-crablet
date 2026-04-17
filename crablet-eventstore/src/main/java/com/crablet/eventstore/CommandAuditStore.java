package com.crablet.eventstore;

/**
 * Stores command metadata for audit and query purposes.
 * <p>
 * This interface is separate from {@link EventStore} so that non-command consumers
 * (views, outbox, automations) are not exposed to command-audit concerns.
 * <p>
 * {@link com.crablet.eventstore.internal.EventStoreImpl} implements both
 * {@code EventStore} and {@code CommandAuditStore}. Within the command framework,
 * The command executor casts the transaction-scoped
 * store to {@code CommandAuditStore} to call this method inside the same transaction
 * that appended the events.
 */
public interface CommandAuditStore {

    /**
     * Store a command for audit and query purposes.
     * Must be called within the same transaction as the event append.
     *
     * @param commandJson   the command serialized as JSON
     * @param commandType   the command type identifier
     * @param transactionId the transaction ID linking this command to its events
     */
    void storeCommand(String commandJson, String commandType, String transactionId);
}
