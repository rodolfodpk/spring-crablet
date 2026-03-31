package com.crablet.command;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;

import java.util.List;

/**
 * Result of command handling containing events and append condition.
 * Following DCB pattern: handlers build decision model, create events,
 * and return condition for atomic constraint enforcement.
 * <p>
 * The reason field provides context when operation is idempotent (empty events).
 */
public record CommandResult(
        List<AppendEvent> events,
        AppendCondition appendCondition,
        String reason  // Reason for idempotency (null when operation creates events)
) {
    /**
     * Creates a result with the events to append and the DCB condition to enforce.
     * This is the standard factory — use it in every {@link CommandHandler#handle} implementation.
     */
    public static CommandResult of(List<AppendEvent> events, AppendCondition condition) {
        return new CommandResult(events, condition, null);
    }

    /**
     * No-op result for handlers that detect the operation has already been applied
     * (idempotent re-execution). {@link com.crablet.command.CommandExecutor} skips
     * the append when this is returned.
     */
    public static CommandResult empty() {
        return new CommandResult(List.of(), AppendCondition.empty(), null);
    }

    /**
     * Returns {@code true} when no events were generated, i.e., the result was produced
     * by {@link #empty()} or by {@link #of} with an empty event list.
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }
}

