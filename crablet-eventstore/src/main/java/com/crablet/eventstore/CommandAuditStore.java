package com.crablet.eventstore;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

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
     * Write a command record within the current transaction using a single SQL path.
     *
     * <p>Executes:
     * <pre>{@code
     * INSERT INTO commands (command_id, transaction_id, type, data, metadata, occurred_at)
     * VALUES (COALESCE(?::uuid, gen_random_uuid()), pg_current_xact_id(), ...)
     * ON CONFLICT (command_id) DO NOTHING
     * }</pre>
     *
     * <p>If {@code commandId} is non-null (client-provided), it is used as the primary key
     * and this returns {@code false} when a committed command with that ID already exists.
     *
     * <p>If {@code commandId} is null, {@code gen_random_uuid()} fills it; {@code ON CONFLICT}
     * never fires and this always returns {@code true}.
     *
     * <p>{@code transaction_id} is always {@code pg_current_xact_id()}, so this must be called
     * inside the active transaction when event-to-command linkage matters.
     *
     * @param commandJson  the command serialized as JSON
     * @param commandType  the command type identifier
     * @param commandId    caller-provided UUID used as PK (UUID v7 recommended), or {@code null}
     *                     for the non-idempotent audit path
     * @param occurredAt   timestamp for the command record
     * @return {@code true} if newly inserted (proceed with handler),
     *         {@code false} if {@code commandId} already committed (short-circuit to idempotent result)
     */
    boolean storeCommand(String commandJson, String commandType, @Nullable UUID commandId, Instant occurredAt);
}
