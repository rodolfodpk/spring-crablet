package com.crablet.eventstore;


/**
 * Stores command metadata for audit and query purposes.
 * <p>
 * This interface is separate from {@link EventStore} so that non-command consumers
 * (views, outbox, automations) are not exposed to command-audit concerns.
 * <p>
 * {@link com.crablet.eventstore.internal.EventStoreImpl} implements both
 * {@code EventStore} and {@code CommandAuditStore}. Within the command framework,
 * the command executor casts the transaction-scoped store to {@code CommandAuditStore}
 * to call these methods inside the same transaction that appends the events.
 */
@Stable
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

    /**
     * Attempt to reserve a command slot using the given idempotency key.
     *
     * <p>Inserts a command record at the start of the transaction using
     * {@code pg_current_xact_id()} so the same transaction ID is shared with
     * any subsequent event append. Must be called before the command handler runs.
     *
     * <p>If the idempotency key already exists in a committed row, returns
     * {@code false} — the caller should short-circuit and return an idempotent result
     * without executing the handler.
     *
     * <p>If the transaction rolls back, the inserted row is rolled back atomically —
     * the key is released and the next attempt will proceed as a new execution.
     *
     * @param commandJson    the command serialized as JSON
     * @param commandType    the command type identifier
     * @param idempotencyKey caller-provided key unique to this command execution
     * @param occurredAt     timestamp for the command record
     * @return {@code true} if newly reserved (proceed), {@code false} if duplicate (short-circuit)
     */
    default boolean reserveCommand(String commandJson, String commandType,
                                   String idempotencyKey, java.time.Instant occurredAt) {
        throw new UnsupportedOperationException("reserveCommand not supported by this CommandAuditStore");
    }
}
